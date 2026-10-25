package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.AdminSettingsRequest
import com.starter.dto.StorageConnectionRequest
import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings
import com.starter.model.postgres.StorageConnection
import com.starter.repository.postgres.AppSettingsRepository
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.StorageConnectionRepository
import com.starter.security.testEncryption
import com.starter.service.storage.GcsStorageAdapter
import com.starter.service.storage.objectStorageAdapter
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Optional

class GcsStorageSettingsTest {
    private val repository = mock(AppSettingsRepository::class.java)
    private val settings = AdminSettingsService(repository, testEncryption())

    @Test
    fun `GCS uses server identity without access keys region or project ID`() {
        val validated = settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", useServerIdentity = true))
        assertThat(validated.storageAccessKey).isNull()
        assertThat(validated.storageSecretKey).isNull()
        assertThat(validated.storageRegion).isNull()
        assertThat(objectStorageAdapter(validated)).isInstanceOf(GcsStorageAdapter::class.java)
        verifyNoInteractions(repository)
    }

    @Test
    fun `service account JSON passes request validation and is retained for blank edits only at the same location`() {
        val key = serviceAccountJson()
        Validation.buildDefaultValidatorFactory().use { factory ->
            assertThat(factory.validator.validate(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", storageSecretKey = key))).isEmpty()
            assertThat(factory.validator.validate(StorageConnectionRequest("GCP", "GCS", "docs", secretKey = key))).isEmpty()
        }
        val saved = AppSettings(storageProvider = "GCS", storageBucket = "docs", storageSecretKey = key)
        val validated = settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", useServerIdentity = false), saved)
        assertThat(validated.storageSecretKey).isEqualTo(key)
        assertThatThrownBy { settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "other", useServerIdentity = false), saved) }
            .isInstanceOf(BadRequestException::class.java)
        assertThat(settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", useServerIdentity = true), saved).storageSecretKey).isNull()
    }

    @Test
    fun `named GCS connections save and retain JSON without exposing secrets`() {
        val connections = mock(StorageConnectionRepository::class.java)
        val items = mock(ItemRepository::class.java)
        val service = StorageConnectionService(connections, items, settings, mock(ItemService::class.java), mock(AuditLogService::class.java), testEncryption())
        val key = serviceAccountJson()
        `when`(connections.save(any(StorageConnection::class.java))).thenAnswer { it.arguments[0] }
        val response = service.save(null, StorageConnectionRequest("GCP archive", "GCS", "docs", secretKey = key), "admin@test.local")
        assertThat(response.secretConfigured).isTrue()
        assertThat(response.accessKeyConfigured).isFalse()
        assertThat(jacksonObjectMapper().writeValueAsString(response)).doesNotContain("private_key", "test-key", "client_email")
        val saved = StorageConnection(nickname = "GCP archive", provider = "GCS", bucket = "docs", secretKey = key)
        `when`(connections.findById(saved.id)).thenReturn(Optional.of(saved))
        service.save(saved.id, StorageConnectionRequest("Renamed", "GCS", "docs"), "admin@test.local")
        assertThat(saved.secretKey).isEqualTo(key)
        assertThatThrownBy { service.save(saved.id, StorageConnectionRequest("Moved", "GCS", "different"), "admin@test.local") }
            .isInstanceOf(BadRequestException::class.java).hasMessageContaining("service account JSON")
        service.save(saved.id, StorageConnectionRequest("Identity", "GCS", "docs", useServerIdentity = true), "admin@test.local")
        assertThat(saved.secretKey).isNull()
    }

    @Test
    fun `invalid keys other ADC types access keys and custom endpoints are rejected without leaking secrets`() {
        listOf("private-invalid-key", """{"type":"external_account","credential_source":{"file":"private-path"}}""",
            serviceAccountJson().replace("https://oauth2.googleapis.com/token", "https://untrusted.example/token")).forEach { key ->
            assertThatThrownBy { settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", storageSecretKey = key)) }
                .isInstanceOf(BadRequestException::class.java).hasMessageContaining("service account JSON")
                .hasMessageNotContaining("private-invalid-key").hasMessageNotContaining("private-path")
        }
        assertThatThrownBy { settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", storageAccessKey = "access")) }
            .isInstanceOf(BadRequestException::class.java).hasMessageContaining("not an access key")
        assertThatThrownBy { settings.validatedStorageSettings(AdminSettingsRequest(storageProvider = "GCS", storageBucket = "docs", storageEndpoint = "https://custom.example")) }
            .isInstanceOf(BadRequestException::class.java).hasMessageContaining("standard Google API endpoint")
        verifyNoInteractions(repository)
    }

    private fun serviceAccountJson(): String {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        return jacksonObjectMapper().writeValueAsString(mapOf(
            "type" to "service_account", "project_id" to "test-project", "private_key_id" to "test-key",
            "private_key" to "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(key.encoded)}\n-----END PRIVATE KEY-----\n",
            "client_email" to "test@test-project.iam.gserviceaccount.com", "client_id" to "12345",
            "token_uri" to "https://oauth2.googleapis.com/token",
        ))
    }
}
