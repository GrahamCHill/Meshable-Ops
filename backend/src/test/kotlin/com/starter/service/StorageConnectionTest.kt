package com.starter.service

import com.starter.dto.AdminSettingsRequest
import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

class StorageConnectionTest {
    private val repository = mock(AppSettingsRepository::class.java)
    private val settings = AdminSettingsService(repository)
    private val storage = FolderStorageService(settings)

    @Test
    fun `invalid drafts cannot overwrite a saved connection`() {
        val existing = AppSettings(storageProvider = "S3", storageBucket = "original")
        `when`(repository.findTopByOrderByCreatedAtAsc()).thenReturn(existing)
        assertThatThrownBy {
            settings.updateSettings(AdminSettingsRequest(storageProvider = "AZURE_BLOB", storageBucket = "docs"))
        }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("endpoint")
        assertThat(existing.storageBucket).isEqualTo("original")
        verify(repository, never()).save(any(AppSettings::class.java))
    }

    @Test
    fun `partial credentials and unsafe endpoints are rejected before network access`() {
        assertThatThrownBy {
            storage.testConnection(AdminSettingsRequest(storageProvider = "S3", storageBucket = "docs", storageAccessKey = "only-one-key"))
        }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("both credential fields")
        listOf("file:///tmp/storage", "https://name:secret@example.com", "https://example.com?token=secret").forEach { endpoint ->
            assertThatThrownBy {
                storage.testConnection(AdminSettingsRequest(storageProvider = "S3", storageBucket = "docs", storageEndpoint = endpoint))
            }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("service endpoint")
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `S3 draft test lists only one object in the selected prefix without saving or writing`() {
        val requests = mutableListOf<String>()
        withServer(200, "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Name>docs</Name><KeyCount>0</KeyCount><MaxKeys>1</MaxKeys><IsTruncated>false</IsTruncated></ListBucketResult>", requests) { endpoint ->
            val response = storage.testConnection(AdminSettingsRequest(
                storageProvider = "S3", storageBucket = "docs", storageEndpoint = endpoint,
                storageAccessKey = "test-access", storageSecretKey = "test-secret", storageRootPath = " /contracts/documents/ ",
            ))
            assertThat(response.message).contains("Connection verified")
        }
        assertThat(requests).hasSize(1)
        assertThat(requests.single()).startsWith("GET /docs?").contains("prefix=contracts/documents/", "max-keys=1")
        verifyNoInteractions(repository)
    }

    @Test
    fun `Azure draft test never attempts to create the container`() {
        val requests = mutableListOf<String>()
        withServer(200, "<EnumerationResults ServiceEndpoint=\"http://localhost/\" ContainerName=\"docs\"><Prefix>contracts/</Prefix><MaxResults>1</MaxResults><Blobs/><NextMarker/></EnumerationResults>", requests) { endpoint ->
            storage.testConnection(AdminSettingsRequest(
                storageProvider = "AZURE_BLOB", storageBucket = "docs", storageEndpoint = "$endpoint/account",
                storageAccessKey = "account", storageSecretKey = Base64.getEncoder().encodeToString(ByteArray(64) { 1 }),
                storageRootPath = "contracts",
            ))
        }
        assertThat(requests).hasSize(1)
        assertThat(requests.single()).startsWith("GET /account/docs?").contains("prefix=contracts/", "maxresults=1")
        verifyNoInteractions(repository)
    }

    @Test
    fun `access errors give actionable feedback without exposing provider response or credentials`() {
        val requests = mutableListOf<String>()
        withServer(403, "<Error><Code>AccessDenied</Code><Message>private-provider-details</Message></Error>", requests) { endpoint ->
            assertThatThrownBy {
                storage.testConnection(AdminSettingsRequest(
                    storageProvider = "S3", storageBucket = "docs", storageEndpoint = endpoint,
                    storageAccessKey = "test-access", storageSecretKey = "private-secret",
                ))
            }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("ListBucket")
                .hasMessageNotContaining("private-provider-details").hasMessageNotContaining("private-secret")
        }
        verifyNoInteractions(repository)
    }

    private fun withServer(status: Int, body: String, requests: MutableList<String>, run: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${URLDecoder.decode(exchange.requestURI.toString(), StandardCharsets.UTF_8)}"
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { run("http://127.0.0.1:${server.address.port}") } finally { server.stop(0) }
    }
}
