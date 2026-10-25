package com.starter.service

import com.starter.model.postgres.AppSettings
import com.starter.model.postgres.StorageConnection
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import com.starter.repository.postgres.StorageConnectionRepository
import com.starter.service.pdfstorage.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GeneratedPdfDestinationsTest {
    private val settings = mock(AdminSettingsService::class.java)
    private val connections = mock(StorageConnectionRepository::class.java)
    private val cleanup = mock(GeneratedPdfCleanupStore::class.java)
    private val plugins = mock(PluginRegistry::class.java)
    private val configuration = mock(PluginConfigurationService::class.java)
    private val destinations = GeneratedPdfDestinations(settings, connections, cleanup, plugins, configuration)
    init { `when`(cleanup.settings(false)).thenReturn(PdfCleanupSettings()) }

    @Test fun `automatic mode prefers default object storage then an enabled named connection then database`() {
        `when`(settings.currentEntity()).thenReturn(AppSettings(storageProvider = "S3", storageBucket = "default", storageRootPath = "app/docs"))
        assertThat(destinations.resolve()?.values?.get("prefix")).isEqualTo("app/docs/__generated_pdfs__/")
        verifyNoInteractions(connections, plugins, configuration)
        `when`(settings.currentEntity()).thenReturn(AppSettings())
        `when`(connections.findAllByEnabledTrueOrderByNicknameAsc()).thenReturn(listOf(StorageConnection(provider="AZURE_BLOB",bucket="named",basePath="shared/",endpoint="https://example.blob.core.windows.net")))
        assertThat(destinations.resolve()?.provider).isEqualTo("AZURE_BLOB")
        assertThat(destinations.resolve()?.values?.get("prefix")).isEqualTo("shared/__generated_pdfs__/")
        `when`(connections.findAllByEnabledTrueOrderByNicknameAsc()).thenReturn(emptyList())
        assertThat(destinations.resolve()).isNull()
    }

    @Test fun `repointing default storage does not repoint existing PDFs but same-service credential rotation is used`() {
        val location = PdfStorageLocation("S3", "DEFAULT", mapOf("bucket" to "original", "endpoint" to "", "prefix" to "old/__generated_pdfs__/"), mapOf("secretKey" to "original-secret"))
        val file = GeneratedPdfFile(java.util.UUID.randomUUID(),GeneratedPdfKind.REPORT,location,emptyMap(),1,"")
        `when`(settings.currentEntity()).thenReturn(AppSettings(storageProvider="S3",storageBucket="new",storageSecretKey="new-secret"))
        assertThat(destinations.credentials(file).location.credentials["secretKey"]).isEqualTo("original-secret")
        `when`(settings.currentEntity()).thenReturn(AppSettings(storageProvider="S3",storageBucket="original",storageSecretKey="rotated-secret"))
        val rotated = destinations.credentials(file)
        assertThat(rotated.location.credentials["secretKey"]).isEqualTo("rotated-secret")
        assertThat(rotated.location.values["prefix"]).isEqualTo("old/__generated_pdfs__/")
    }

    @Test fun `administrator selected Drive storage works for document owners without Drive browsing access`() {
        val values=mapOf("clientId" to "app-client","clientSecret" to "test-secret","refreshToken" to "test-refresh","rootFolderId" to "configured-folder")
        `when`(configuration.current("google-drive")).thenReturn(values)
        `when`(plugins.missingConfiguration("google-drive",values)).thenReturn(emptyList())
        doThrow(com.starter.exception.ForbiddenException("Drive browsing is not granted")).`when`(plugins).requireAvailable("google-drive","ADMIN")
        assertThat(destinations.resolve(PdfStorageDestination.GOOGLE_DRIVE,null)?.provider).isEqualTo("GOOGLE_DRIVE")
        verify(plugins).requireEnabled("google-drive","ADMIN")
        verify(plugins,never()).requireAvailable("google-drive","ADMIN")
    }
}
