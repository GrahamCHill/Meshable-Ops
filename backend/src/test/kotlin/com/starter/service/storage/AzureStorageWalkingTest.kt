package com.starter.service.storage

import com.starter.model.postgres.AppSettings
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

class AzureStorageWalkingTest {
    @Test fun `counting 2500 blobs follows markers excludes exports and never creates the container`() {
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI}"
            val start = Regex("(?:^|&)marker=(\\d+)").find(exchange.requestURI.rawQuery.orEmpty())?.groupValues?.get(1)?.toInt() ?: 0
            val objects = List(2500) { "folders/a/nested/$it.txt" } + "folders/a/__generated_pdfs__/report.pdf" + "folders/b/"
            val page = objects.drop(start).take(1000)
            val next = if (start + page.size < objects.size) (start + page.size).toString() else ""
            val body = buildString {
                append("<EnumerationResults><Blobs>")
                page.forEach { append("<Blob><Name>$it</Name><Properties><Content-Length>12</Content-Length><BlobType>BlockBlob</BlobType></Properties></Blob>") }
                append("</Blobs><NextMarker>$next</NextMarker></EnumerationResults>")
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val settings = AppSettings(storageProvider = "AZURE_BLOB", storageBucket = "docs", storageAccessKey = "account",
                storageSecretKey = Base64.getEncoder().encodeToString(ByteArray(64) { 1 }), storageEndpoint = "http://127.0.0.1:${server.address.port}/account")
            val snapshot = AzureBlobStorageAdapter(settings, "folders/").countRoots()
            assertThat(snapshot.documentCounts).containsEntry("folders/a/", 2500).containsEntry("folders/b/", 0)
            assertThat(requests).hasSize(3).allMatch { it.startsWith("GET ") && it.contains("comp=list") }
        } finally { server.stop(0) }
    }
}
