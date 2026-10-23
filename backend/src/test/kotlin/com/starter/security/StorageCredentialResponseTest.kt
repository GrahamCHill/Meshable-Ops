package com.starter.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.AdminSettingsRequest
import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import com.starter.service.AdminSettingsService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class StorageCredentialResponseTest {
    @Test fun `default storage settings are write only and retain saved keys for a blank edit`() {
        val repository = mock(AppSettingsRepository::class.java)
        val saved = AppSettings(storageProvider = "S3", storageBucket = "docs", storageAccessKey = "private-access", storageSecretKey = "private-secret")
        `when`(repository.findTopByOrderByCreatedAtAsc()).thenReturn(saved)
        `when`(repository.save(any(AppSettings::class.java))).thenAnswer { it.arguments[0] }
        val service = AdminSettingsService(repository, testEncryption())
        val response = service.updateSettings(AdminSettingsRequest(storageProvider = "S3", storageBucket = "docs", useServerIdentity = false))
        assertThat(saved.storageSecretKey).isEqualTo("private-secret")
        assertThat(response.storageAccessKeyConfigured).isTrue()
        assertThat(jacksonObjectMapper().writeValueAsString(response)).doesNotContain("private-access", "private-secret")
        service.updateSettings(AdminSettingsRequest(storageProvider = "S3", storageBucket = "docs", useServerIdentity = true))
        assertThat(saved.storageAccessKey).isNull(); assertThat(saved.storageSecretKey).isNull()
    }
}
