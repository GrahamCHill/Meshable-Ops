package com.starter.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.*
import com.starter.repository.postgres.*
import com.starter.repository.mongo.*
import com.starter.service.BackupExportService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class BackupRedactionTest {
    @Test fun `application backups exclude all provider credentials and password hashes`() {
        val users = mock(UserRepository::class.java)
        val settings = mock(AppSettingsRepository::class.java)
        val connections = mock(StorageConnectionRepository::class.java)
        val ai = mock(AiSettingsRepository::class.java)
        `when`(users.findAllByOrderByRoleAscCreatedAtAsc()).thenReturn(listOf(User(password = "private-password-hash")))
        `when`(settings.findTopByOrderByCreatedAtAsc()).thenReturn(AppSettings(storageAccessKey = "private-default-access", storageSecretKey = "private-default-secret"))
        `when`(connections.findAllByOrderByNicknameAsc()).thenReturn(listOf(StorageConnection(accessKey = "private-connection-access", secretKey = "private-connection-secret")))
        `when`(ai.findById(1L)).thenReturn(Optional.of(AiSettings(chatApiKey = "private-chat-key", embeddingApiKey = "private-embedding-key")))
        val export = BackupExportService(users, mock(ItemRepository::class.java), mock(StorageDocumentRepository::class.java),
            mock(DashboardEmbedConfigRepository::class.java), settings, mock(EventRepository::class.java), mock(ActivityLogRepository::class.java), connections, ai).export()
        assertThat(export["credentialsIncluded"]).isEqualTo(false)
        assertThat(jacksonObjectMapper().writeValueAsString(export)).doesNotContain("private-", "enc:v1:")
    }
}
