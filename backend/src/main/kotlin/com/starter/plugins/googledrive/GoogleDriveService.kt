package com.starter.plugins.googledrive

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.*
import com.starter.plugins.PluginRegistry
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.integrations.*
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GoogleDriveFile(val id:String,val name:String,val folder:Boolean,val size:Long,val modifiedAt:String?,val url:String?)
data class GoogleDriveFiles(val items:List<GoogleDriveFile>,val nextPageToken:String?,val folderId:String,val rootId:String)

@Service
class GoogleDriveService(private val plugins:PluginRegistry,private val settings:PluginConfigurationService,private val http:IntegrationHttpClient) {
    fun files(role:String,folder:String?,pageToken:String?):GoogleDriveFiles {
        plugins.requireAvailable(ID,role)
        if(folder!=null&&!folder.matches(Regex("[A-Za-z0-9_-]{1,200}"))||pageToken!=null&&(pageToken.length>4096||pageToken.any(Char::isISOControl)))throw BadRequestException("Invalid folder or page token")
        val values=settings.current(ID)
        if(plugins.missingConfiguration(ID,values).isNotEmpty())throw BadRequestException("Complete the Google Drive connection settings first")
        plugins.validateConfiguration(ID,values)
        val form=mapOf("client_id" to values.getValue("clientId"),"client_secret" to values.getValue("clientSecret"),"refresh_token" to values.getValue("refreshToken"),"grant_type" to "refresh_token").entries.joinToString("&"){"${encode(it.key)}=${encode(it.value)}"}
        val token=http.request("POST","https://oauth2.googleapis.com/token",body=form,contentType="application/x-www-form-urlencoded").body?.path("access_token")?.asText()?.takeIf {it.isNotBlank()} ?: throw IntegrationUnavailable("Google did not return an access token")
        val headers=mapOf("Authorization" to "Bearer $token")
        fun metadata(id:String)=http.request("GET","https://www.googleapis.com/drive/v3/files/${encode(id)}?fields=id,mimeType,parents,name&supportsAllDrives=true",headers).body?.also {
            com.starter.service.storage.GeneratedPdfNamespace.requireOrdinary(it.path("name").asText())
        } ?: throw IntegrationUnavailable("Folder metadata unavailable")
        val root=metadata(values.getValue("rootFolderId"))
        if(root.path("mimeType").asText()!=FOLDER)throw BadRequestException("The configured root must be a folder")
        val rootId=root.path("id").asText().takeIf {it.matches(Regex("[A-Za-z0-9_-]{1,200}"))} ?: throw IntegrationUnavailable("Google returned an invalid root")
        val target=folder?:rootId
        // Follow real parent IDs, never shortcuts or caller-supplied ancestry. Bound depth/cycles.
        var ancestor=target;val seen=mutableSetOf<String>()
        while(ancestor!=rootId) {
            if(!seen.add(ancestor)||seen.size>30)throw ForbiddenException("Folder is outside the configured root")
            val node=metadata(ancestor)
            if(node.path("mimeType").asText()!=FOLDER)throw ForbiddenException("Choose a folder inside the configured root")
            ancestor=node.path("parents").firstOrNull()?.asText()?.takeIf {it.matches(Regex("[A-Za-z0-9_-]{1,200}"))} ?: throw ForbiddenException("Folder is outside the configured root")
        }
        plugins.requireAvailable(ID,role)
        val query=encode("'$target' in parents and trashed = false")
        val url="https://www.googleapis.com/drive/v3/files?q=$query&pageSize=100&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime,webViewLink)&orderBy=folder,name&supportsAllDrives=true&includeItemsFromAllDrives=true"+(pageToken?.let {"&pageToken=${encode(it)}"}?:"")
        val page=http.request("GET",url,headers).body ?: throw IntegrationUnavailable("Google did not return files")
        if(!page.path("files").isArray)throw IntegrationUnavailable("Google returned an unsupported file listing")
        val result=GoogleDriveFiles(page.path("files").take(100).filterNot {com.starter.service.storage.GeneratedPdfNamespace.excluded(it.path("name").asText())}.map(::file),page.path("nextPageToken").asText().takeIf {it.isNotBlank()&&it.length<=4096},target,rootId)
        plugins.requireAvailable(ID,role)
        if(settings.current(ID)!=values)throw ConflictException("Google Drive settings changed. Reload the folder.")
        return result
    }
    private fun file(node:JsonNode):GoogleDriveFile {
        val id=node.path("id").asText();if(!id.matches(Regex("[A-Za-z0-9_-]{1,200}")))throw IntegrationUnavailable("Google returned an invalid file ID")
        val raw=node.path("webViewLink").asText();val uri=runCatching {URI(raw)}.getOrNull()
        val url=raw.takeIf {uri?.scheme=="https"&&uri.host in setOf("drive.google.com","docs.google.com")&&uri.userInfo==null&&uri.port in listOf(-1,443)}
        return GoogleDriveFile(id,node.path("name").asText().take(1000),node.path("mimeType").asText()==FOLDER,node.path("size").asLong(0),node.path("modifiedTime").asText().takeIf {it.isNotBlank()},url)
    }
    private fun encode(value:String)=URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20")
    companion object {const val ID="google-drive";const val FOLDER="application/vnd.google-apps.folder"}
}
