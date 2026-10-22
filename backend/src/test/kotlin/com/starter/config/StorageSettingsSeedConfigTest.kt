package com.starter.config

import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.boot.DefaultApplicationArguments

class StorageSettingsSeedConfigTest {
    private val repository = mock(AppSettingsRepository::class.java)

    @Test
    fun `disabled storage seeding leaves the repository untouched`() {
        StorageSettingsSeedConfig().seedStorageSettingsRunner(repository, StorageSeedProperties())
            .run(DefaultApplicationArguments())

        verifyNoInteractions(repository)
    }

    @Test
    fun `existing settings are preserved unless force is enabled`() {
        val existing = AppSettings(storageProvider = "S3", storageBucket = "existing")
        `when`(repository.findTopByOrderByCreatedAtAsc()).thenReturn(existing)
        val seed = StorageSeedProperties().apply { provider = "AZURE_BLOB" }

        StorageSettingsSeedConfig().seedStorageSettingsRunner(repository, seed)
            .run(DefaultApplicationArguments())

        assertThat(existing.storageProvider).isEqualTo("S3")
        assertThat(existing.storageBucket).isEqualTo("existing")
        verify(repository).findTopByOrderByCreatedAtAsc()
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun `forced seeding normalizes paths and keeps workload credentials optional`() {
        val existing = AppSettings(storageProvider = "S3", storageBucket = "existing")
        `when`(repository.findTopByOrderByCreatedAtAsc()).thenReturn(existing)
        val seed = StorageSeedProperties().apply {
            provider = " azure_blob "
            bucket = " documents "
            region = " "
            endpoint = " https://example.blob.core.windows.net "
            rootPath = "\\contracts\\documents\\"
            force = true
        }

        StorageSettingsSeedConfig().seedStorageSettingsRunner(repository, seed)
            .run(DefaultApplicationArguments())

        val saved = ArgumentCaptor.forClass(AppSettings::class.java)
        verify(repository).save(saved.capture())
        assertThat(saved.value.id).isEqualTo(existing.id)
        assertThat(saved.value.storageProvider).isEqualTo("AZURE_BLOB")
        assertThat(saved.value.storageBucket).isEqualTo("documents")
        assertThat(saved.value.storageRegion).isNull()
        assertThat(saved.value.storageEndpoint).isEqualTo("https://example.blob.core.windows.net")
        assertThat(saved.value.storageAccessKey).isNull()
        assertThat(saved.value.storageSecretKey).isNull()
        assertThat(saved.value.storageRootPath).isEqualTo("contracts/documents")
    }
}
