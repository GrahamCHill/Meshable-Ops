package com.starter.service.pdfstorage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.integrations.IntegrationBytesResponse
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.plugins.integrations.IntegrationUnavailable
import com.starter.service.PdfService
import com.starter.service.storage.GeneratedPdfNamespace
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Writes only UUID-named PDFs beneath the reserved folder, never to a caller-supplied path or URL. */
@Component
class GeneratedPdfDriveBackend(private val http: IntegrationHttpClient, private val json: ObjectMapper) : GeneratedPdfBackend {
    override fun supports(provider: String) = provider in setOf("GOOGLE_DRIVE", "ONEDRIVE", "SHAREPOINT")
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    private fun identifier(value: String): String = value.also { require(it.matches(Regex("[A-Za-z0-9!_,.-]{1,200}"))) }
    private fun form(values: Map<String, String>) = values.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
    private fun node(response: IntegrationBytesResponse): JsonNode = json.readTree(response.bytes)

    private fun headers(location: PdfStorageLocation): Map<String, String> {
        val values = location.values + location.credentials
        val google = location.provider == "GOOGLE_DRIVE"
        val url = if (google) "https://oauth2.googleapis.com/token" else "https://login.microsoftonline.com/${identifier(values.getValue("tenantId"))}/oauth2/v2.0/token"
        val request = if (google) mapOf("client_id" to values.getValue("clientId"), "client_secret" to values.getValue("clientSecret"),
            "refresh_token" to values.getValue("refreshToken"), "grant_type" to "refresh_token")
        else mapOf("client_id" to values.getValue("clientId"), "client_secret" to values.getValue("clientSecret"), "scope" to "https://graph.microsoft.com/.default", "grant_type" to "client_credentials")
        val token = http.request("POST", url, body = form(request), contentType = "application/x-www-form-urlencoded").body?.path("access_token")?.asText()?.takeIf { it.isNotBlank() }
            ?: throw IntegrationUnavailable("Drive authentication unavailable")
        return mapOf("Authorization" to "Bearer $token")
    }

    override fun prepare(location: PdfStorageLocation, id: UUID): Map<String, String> {
        val auth = headers(location)
        val folder = GeneratedPdfNamespace.FOLDER
        if (location.provider == "GOOGLE_DRIVE") {
            val root = identifier(location.values.getValue("rootFolderId"))
            val rootMetadata = http.request("GET", "https://www.googleapis.com/drive/v3/files/${encode(root)}?fields=id,mimeType&supportsAllDrives=true", auth).body
                ?: throw IntegrationUnavailable("Google root folder unavailable")
            require(rootMetadata.path("mimeType").asText() == "application/vnd.google-apps.folder")
            val parent = identifier(rootMetadata.path("id").asText())
            val query = encode("'$parent' in parents and name = '$folder' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            val existing = http.request("GET", "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)&supportsAllDrives=true&includeItemsFromAllDrives=true", auth).body?.path("files")?.firstOrNull()?.path("id")?.asText()
            val folderId = existing ?: http.request("POST", "https://www.googleapis.com/drive/v3/files?fields=id&supportsAllDrives=true", auth,
                json.writeValueAsString(mapOf("name" to folder, "mimeType" to "application/vnd.google-apps.folder", "parents" to listOf(parent)))).body?.path("id")?.asText()
                ?: throw IntegrationUnavailable("Could not create the generated PDF folder")
            val fileId = http.request("GET", "https://www.googleapis.com/drive/v3/files/generateIds?count=1&space=drive&type=files", auth).body?.path("ids")?.firstOrNull()?.asText()
                ?: throw IntegrationUnavailable("Could not allocate a generated PDF file")
            return mapOf("folderId" to identifier(folderId), "fileId" to identifier(fileId), "name" to "$id.pdf")
        }
        val drive = "https://graph.microsoft.com/v1.0/drives/${encode(identifier(location.values.getValue("driveId")))}"
        val root = location.values["rootItemId"]?.ifBlank { null }?.let { "items/${encode(identifier(it))}" } ?: "root"
        fun lookup() = http.binary("GET", "$drive/$root:/$folder?\$select=id,folder", auth, allowedStatuses = setOf(404))
        var response = lookup()
        if (response.status == 404) {
            response = http.binary("POST", "$drive/$root/children", auth,
                json.writeValueAsBytes(mapOf("name" to folder, "folder" to emptyMap<String, String>(), "@microsoft.graph.conflictBehavior" to "fail")),
                "application/json", allowedStatuses = setOf(409))
            if (response.status == 409) response = lookup()
        }
        val entry = node(response)
        require(entry.path("folder").isObject)
        return mapOf("folderId" to identifier(entry.path("id").asText()), "name" to "$id.pdf")
    }

    private fun validate(file: GeneratedPdfFile) {
        require(file.locator.getValue("name") == "${file.id}.pdf")
        identifier(file.locator.getValue("folderId"))
        if (file.location.provider == "GOOGLE_DRIVE") identifier(file.locator.getValue("fileId"))
    }
    private fun graphPath(file: GeneratedPdfFile) = "https://graph.microsoft.com/v1.0/drives/${encode(identifier(file.location.values.getValue("driveId")))}/items/${encode(identifier(file.locator.getValue("folderId")))}:/${file.id}.pdf"

    override fun upload(file: GeneratedPdfFile, bytes: ByteArray) {
        validate(file)
        val auth = headers(file.location)
        if (file.location.provider == "GOOGLE_DRIVE") {
            val metadata = mapOf("id" to file.locator.getValue("fileId"), "name" to file.locator.getValue("name"), "mimeType" to "application/pdf",
                "parents" to listOf(file.locator.getValue("folderId")), "appProperties" to mapOf("meshableOpsGeneratedPdf" to "true"))
            val session = http.binary("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true", auth +
                mapOf("X-Upload-Content-Type" to "application/pdf", "X-Upload-Content-Length" to bytes.size.toString()), json.writeValueAsBytes(metadata), "application/json")
            val url = session.headers["Location"] ?: throw IntegrationUnavailable("Google upload session unavailable")
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "www.googleapis.com" && uri.path == "/upload/drive/v3/files" && uri.userInfo == null && uri.fragment == null && uri.port in listOf(-1,443))
            http.binary("PUT", url, auth, bytes)
        } else http.binary("PUT", graphPath(file) + ":/content", auth, bytes)
    }

    override fun download(file: GeneratedPdfFile): ByteArray {
        validate(file)
        val auth = headers(file.location)
        if (file.location.provider == "GOOGLE_DRIVE") return http.binary("GET",
            "https://www.googleapis.com/drive/v3/files/${encode(file.locator.getValue("fileId"))}?alt=media&supportsAllDrives=true", auth, maxBytes = PdfService.MAX_BYTES).bytes
        val metadata = node(http.binary("GET", graphPath(file) + "?\$select=id", auth))
        val item = encode(identifier(metadata.path("id").asText()))
        val content = http.binary("GET", "https://graph.microsoft.com/v1.0/drives/${encode(identifier(file.location.values.getValue("driveId")))}/items/$item/content",
            auth, allowedStatuses = setOf(302), maxBytes = PdfService.MAX_BYTES)
        return if (content.status == 302) http.microsoftDownload(content.headers["Location"] ?: throw IntegrationUnavailable("Microsoft download location unavailable"), PdfService.MAX_BYTES) else content.bytes
    }

    override fun delete(file: GeneratedPdfFile) {
        validate(file)
        val auth = headers(file.location)
        if (file.location.provider == "GOOGLE_DRIVE") {
            http.binary("DELETE", "https://www.googleapis.com/drive/v3/files/${encode(file.locator.getValue("fileId"))}?supportsAllDrives=true", auth, allowedStatuses = setOf(404))
        } else {
            val metadata = http.binary("GET", graphPath(file) + "?\$select=id", auth, allowedStatuses = setOf(404))
            if (metadata.status == 404) return
            val item = encode(identifier(node(metadata).path("id").asText()))
            http.binary("POST", "https://graph.microsoft.com/v1.0/drives/${encode(identifier(file.location.values.getValue("driveId")))}/items/$item/permanentDelete", auth, allowedStatuses = setOf(404))
        }
    }
}
