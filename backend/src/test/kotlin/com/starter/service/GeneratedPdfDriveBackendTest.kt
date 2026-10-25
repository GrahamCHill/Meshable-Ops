package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.integrations.*
import com.starter.service.pdfstorage.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import java.util.UUID

class GeneratedPdfDriveBackendTest {
    private val json = jacksonObjectMapper()
    private data class Call(val method: String, val url: String, val headers: Map<String,String>, val body: ByteArray?)
    private val calls = mutableListOf<Call>()
    private val bytes = "saved-pdf".toByteArray()
    private var uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?upload_id=test-session"
    private val http = mock(IntegrationHttpClient::class.java) { invocation ->
        when (invocation.method.name) {
            "request" -> {
                val method = invocation.arguments[0] as String
                val url = invocation.arguments[1] as String
                @Suppress("UNCHECKED_CAST") val headers = invocation.arguments[2] as Map<String,String>
                calls += Call(method,url,headers,(invocation.arguments[3] as String?)?.toByteArray())
                val body = when {
                    url.endsWith("/token") -> """{"access_token":"test-only-token"}"""
                    url.contains("/files/root?") -> """{"id":"real-root","mimeType":"application/vnd.google-apps.folder"}"""
                    url.contains("generateIds") -> """{"ids":["allocated-file"]}"""
                    method == "POST" -> """{"id":"pdf-folder"}"""
                    else -> """{"files":[]}"""
                }
                IntegrationResponse(200,json.readTree(body))
            }
            "binary" -> {
                val method = invocation.arguments[0] as String
                val url = invocation.arguments[1] as String
                @Suppress("UNCHECKED_CAST") val headers = invocation.arguments[2] as Map<String,String>
                calls += Call(method,url,headers,invocation.arguments[3] as ByteArray?)
                when {
                    url.contains("uploadType=resumable") -> IntegrationBytesResponse(200,byteArrayOf(),mapOf("Location" to uploadUrl))
                    url.contains("alt=media") -> IntegrationBytesResponse(200,bytes)
                    url.endsWith("permanentDelete") || method == "DELETE" -> IntegrationBytesResponse(204,byteArrayOf())
                    url.endsWith("/content") && method == "GET" -> IntegrationBytesResponse(302,byteArrayOf(),mapOf("Location" to "https://tenant.sharepoint.com/content?sig=test"))
                    url.contains(":/__generated_pdfs__?") -> IntegrationBytesResponse(404,"{}".toByteArray())
                    url.endsWith("/children") -> IntegrationBytesResponse(201,"""{"id":"pdf-folder","folder":{}}""".toByteArray())
                    else -> IntegrationBytesResponse(200,"""{"id":"stored-item"}""".toByteArray())
                }
            }
            "microsoftDownload" -> bytes
            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val backend = GeneratedPdfDriveBackend(http,json)
    private fun location(provider:String) = PdfStorageLocation(provider,provider,mapOf("clientId" to "test-client","tenantId" to "tenant-id","rootFolderId" to "root","driveId" to "test-drive"),mapOf("clientSecret" to "test-secret","refreshToken" to "test-refresh"))

    @Test fun `Google PDFs use preallocated IDs resumable uploads and bounded binary downloads`() {
        val id=UUID.randomUUID(); val location=location("GOOGLE_DRIVE")
        val locator=backend.prepare(location,id)
        assertThat(locator).containsEntry("fileId","allocated-file").containsEntry("name","$id.pdf")
        val file=GeneratedPdfFile(id,GeneratedPdfKind.DOCUMENT,location,locator,bytes.size,"")
        backend.upload(file,bytes)
        assertThat(backend.download(file)).isEqualTo(bytes)
        backend.delete(file)
        val metadata=json.readTree(calls.single {it.url.contains("uploadType=resumable")}.body)
        assertThat(metadata.path("id").asText()).isEqualTo("allocated-file")
        assertThat(metadata.path("parents").single().asText()).isEqualTo("pdf-folder")
        assertThat(calls.single {it.method=="PUT"}.body).isEqualTo(bytes)
        assertThat(calls.last().url).contains("/allocated-file?supportsAllDrives=true")
    }

    @Test fun `Microsoft PDFs stay inside the reserved folder and cleanup permanently removes their own item`() {
        val id=UUID.randomUUID();val location=location("ONEDRIVE")
        val file=GeneratedPdfFile(id,GeneratedPdfKind.REPORT,location,backend.prepare(location,id),bytes.size,"")
        backend.upload(file,bytes)
        assertThat(backend.download(file)).isEqualTo(bytes)
        backend.delete(file)
        assertThat(calls.single {it.method=="PUT"}.url).endsWith("/items/pdf-folder:/$id.pdf:/content")
        verify(http).microsoftDownload("https://tenant.sharepoint.com/content?sig=test",PdfService.MAX_BYTES)
        assertThat(calls.last().url).endsWith("/items/stored-item/permanentDelete")
        assertThat(calls.last().method).isEqualTo("POST")
    }

    @Test fun `provider upload sessions cannot forward credentials to a different origin`() {
        val id=UUID.randomUUID(); val location=location("GOOGLE_DRIVE")
        val file=GeneratedPdfFile(id,GeneratedPdfKind.DOCUMENT,location,backend.prepare(location,id),bytes.size,"")
        uploadUrl="https://attacker.example/upload"
        assertThatThrownBy {backend.upload(file,bytes)}.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(calls.none {it.url.contains("attacker.example")}).isTrue()
        assertThatThrownBy { IntegrationHttpClient(json).microsoftDownload("https://attacker.example/pdf",1000) }.isInstanceOf(IntegrationUnavailable::class.java)
    }
}
