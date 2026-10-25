package com.starter.service.storage

import com.google.cloud.NoCredentials
import com.google.cloud.storage.StorageOptions
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.model.postgres.AppSettings
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

class GcsStorageAdapterTest {
    @Test
    fun `connection probe lists one object inside its boundary without writes`() {
        withStorage({ reply(it, "{}") }) { adapter, requests ->
            adapter.testConnection()
            assertThat(requests).hasSize(1)
            assertThat(requests.single()).startsWith("GET /storage/v1/b/docs/o?").contains("prefix=contracts/", "maxResults=1")
        }
    }

    @Test
    fun `browse follows pages and returns folders before files relative to the shared root`() {
        withStorage({ exchange ->
            reply(exchange, if (exchange.requestURI.rawQuery.orEmpty().contains("pageToken=next"))
                """{"items":[{"bucket":"docs","name":"contracts/legal/z.pdf","size":"5","contentType":"application/pdf"}]}"""
            else """{"prefixes":["contracts/legal/archive/"],"nextPageToken":"next","items":[{"bucket":"docs","name":"contracts/legal/","size":"0"}]}""")
        }) { adapter, requests ->
            val result = adapter.browse("contracts/", "legal/")
            assertThat(result.provider).isEqualTo("GCS")
            assertThat(result.path).isEqualTo("legal/")
            assertThat(result.entries.map { it.path }).containsExactly("legal/archive/", "legal/z.pdf")
            assertThat(result.entries.map { it.type }).containsExactly("FOLDER", "OBJECT")
            assertThat(result.entries.last().contentType).isEqualTo("application/pdf")
            assertThat(requests).hasSize(2).allMatch { it.contains("prefix=contracts/legal/") && it.contains("delimiter=/") }
        }
    }

    @Test
    fun `recursive indexing skips folder markers and counts managed roots across pages`() {
        withStorage({ exchange ->
            reply(exchange, if (exchange.requestURI.rawQuery.orEmpty().contains("pageToken=next"))
                """{"items":[{"bucket":"docs","name":"contracts/a/nested/b.txt","size":"2"}]}"""
            else """{"nextPageToken":"next","items":[{"bucket":"docs","name":"contracts/a/","size":"0"},{"bucket":"docs","name":"contracts/a/a.txt","size":"1"},{"bucket":"docs","name":"contracts/unregistered/x.txt","size":"1"}]}""")
        }) { adapter, _ ->
            val snapshot = adapter.inspectAndEnsureRoots(listOf("contracts/a/"))
            assertThat(snapshot.documentCounts).containsEntry("contracts/a/", 2)
            assertThat(adapter.listDocuments("contracts/").map { it.path }).containsExactly("a/a.txt", "a/nested/b.txt", "unregistered/x.txt")
        }
    }

    @Test
    fun `missing roots and uploads write objects with the intended path and content type`() {
        val writes = mutableListOf<String>()
        withStorage({ exchange ->
            if (exchange.requestMethod == "POST") {
                val body = if (exchange.requestHeaders.getFirst("Content-Encoding") == "gzip") GZIPInputStream(exchange.requestBody) else exchange.requestBody
                writes += body.bufferedReader().use { it.readText() }
                reply(exchange, """{"bucket":"docs","name":"contracts/a/","size":"0"}""")
            } else reply(exchange, "{}")
        }) { adapter, requests ->
            assertThat(adapter.inspectAndEnsureRoots(listOf("contracts/a/")).documentCounts).containsEntry("contracts/a/", 0)
            adapter.upload("contracts/a/", "nested/test.txt", "text/plain", "hello".toByteArray())
            assertThat(writes).hasSize(2)
            assertThat(writes[0]).contains("contracts/a/", "application/x-directory")
            assertThat(writes[1]).contains("contracts/a/nested/test.txt", "text/plain", "hello")
            assertThat(requests.count { it.startsWith("POST ") }).isEqualTo(2)
        }
    }

    @Test
    fun `nonempty folders cannot be deleted`() {
        withStorage({ reply(it, """{"items":[{"bucket":"docs","name":"contracts/a/","size":"0"},{"bucket":"docs","name":"contracts/a/child.txt","size":"1"}]}""") }) { adapter, requests ->
            assertThatThrownBy { adapter.deleteFolderIfEmpty("contracts/a/") }.isInstanceOf(ConflictException::class.java)
            assertThat(requests).hasSize(1).allMatch { it.startsWith("GET ") }
        }
    }

    @Test
    fun `empty folders delete only their marker`() {
        withStorage({ exchange ->
            if (exchange.requestMethod == "DELETE") {
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            } else reply(exchange, """{"items":[{"bucket":"docs","name":"contracts/a/","size":"0"}]}""")
        }) { adapter, requests ->
            adapter.deleteFolderIfEmpty("contracts/a/")
            assertThat(requests).hasSize(2)
            assertThat(requests.last()).startsWith("DELETE /storage/v1/b/docs/o/contracts/a/")
        }
    }

    @Test
    fun `download preserves content and metadata within the indexing limit`() {
        withStorage({ exchange ->
            if (exchange.requestURI.rawQuery.orEmpty().contains("alt=media")) reply(exchange, "hello", "text/plain")
            else reply(exchange, """{"bucket":"docs","name":"contracts/a/test.txt","size":"5","contentType":"text/plain","generation":"1"}""")
        }) { adapter, _ ->
            val download = adapter.download("contracts/a/", "test.txt", 5)
            assertThat(download.filename).isEqualTo("test.txt")
            assertThat(download.contentType).isEqualTo("text/plain")
            assertThat(String(download.bytes)).isEqualTo("hello")
        }
    }

    @Test
    fun `oversized downloads are rejected before fetching content`() {
        withStorage({ reply(it, """{"bucket":"docs","name":"contracts/a/test.txt","size":"100","generation":"1"}""") }) { adapter, requests ->
            assertThatThrownBy { adapter.download("contracts/a/", "test.txt", 5) }
                .isInstanceOf(BadRequestException::class.java).hasMessageContaining("indexing size limit")
            assertThat(requests).hasSize(1)
        }
    }

    private fun withStorage(handler: (HttpExchange) -> Unit, run: (GcsStorageAdapter, MutableList<String>) -> Unit) {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${URLDecoder.decode(exchange.requestURI.toString(), StandardCharsets.UTF_8)}"
            handler(exchange)
        }
        server.start()
        val storage = StorageOptions.newBuilder().setHost("http://127.0.0.1:${server.address.port}")
            .setProjectId("test-project").setCredentials(NoCredentials.getInstance())
            .setRetrySettings(StorageOptions.getDefaultRetrySettings().toBuilder().setMaxAttempts(1).build()).build().service
        try {
            run(GcsStorageAdapter(AppSettings(storageProvider = "GCS", storageBucket = "docs"), "contracts/") { storage }, requests)
        } finally {
            storage.close()
            server.stop(0)
        }
    }

    private fun reply(exchange: HttpExchange, body: String, contentType: String = "application/json") {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
