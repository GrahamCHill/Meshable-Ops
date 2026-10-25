package com.starter.plugins.googledrive

import com.starter.plugins.*
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component

@Component
class GoogleDrivePlugin:WorkspacePlugin {
    override val manifest=PluginManifest("google-drive","Google Drive","1.0.0","Browse a selected Google Drive folder with encrypted OAuth credentials.",
        compatibleVersions=listOf("1.0.0"),extensions=setOf(PluginExtension.PAGE,PluginExtension.API,PluginExtension.SETTINGS),defaultRoles=setOf("ADMIN"),
        categories=setOf("Files"),adminMenu=PluginAdminMenu("storage-sharing","Storage & sharing","Google Drive","cloud"),
        capabilities=mapOf("files.google-drive" to "Google Drive folder browsing"),runtimes=setOf(PluginRuntime.REACT,PluginRuntime.KOTLIN),
        settingsFields=listOf(PluginSettingField("clientId","Google OAuth client ID",required=true,maxLength=1000),
            PluginSettingField("clientSecret","Google OAuth client secret",PluginFieldType.SECRET,true,maxLength=4096),
            PluginSettingField("refreshToken","Google refresh token",PluginFieldType.SECRET,true,"Browsing uses drive.metadata.readonly. To store generated PDFs, use an offline token with drive.file access to the selected folder (or drive access).",4096),
            PluginSettingField("rootFolderId","Root folder ID",required=true,help="A Google folder ID, or root for the connected account's My Drive. Members can browse only this folder and its descendants.",maxLength=200)))
    override fun validateConfiguration(values:Map<String,String>) {
        values["rootFolderId"]?.let {if(!it.matches(Regex("[A-Za-z0-9_-]{1,200}")))throw BadRequestException("Use a Google Drive folder ID")}
    }
}
