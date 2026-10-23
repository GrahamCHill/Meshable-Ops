package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.*
import com.starter.exception.ConflictException
import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageConnection
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.StorageConnectionRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class StorageConnectionServiceTest {
    private val connections = mock(StorageConnectionRepository::class.java)
    private val items = mock(ItemRepository::class.java)
    private val settings = AdminSettingsService(mock(com.starter.repository.postgres.AppSettingsRepository::class.java))
    private val itemService = mock(ItemService::class.java)
    private val audit = mock(AuditLogService::class.java)
    private val service = StorageConnectionService(connections, items, settings, itemService, audit)

    @Test
    fun `ordinary storage listing exposes nicknames and counts but no connection secrets or physical paths`() {
        val visible = StorageConnection(nickname = "Legal archive", bucket = "private-bucket", endpoint = "https://private.example", accessKey = "private-access", secretKey = "private-secret")
        val empty = StorageConnection(nickname = "Unconfigured selection")
        `when`(connections.findAllByEnabledTrueOrderByNicknameAsc()).thenReturn(listOf(visible, empty))
        `when`(items.countByStorageConnectionIdAndAccessibleTrue(visible.id)).thenReturn(2L)
        val response = service.listShared()
        assertThat(response).hasSize(1)
        assertThat(response.single().nickname).isEqualTo("Legal archive")
        assertThat(jacksonObjectMapper().writeValueAsString(response)).doesNotContain("private-bucket", "private.example", "private-access", "private-secret")
    }

    @Test
    fun `editing a nickname preserves credentials and never returns the saved secret`() {
        val existing = StorageConnection(nickname = "Old", bucket = "docs", accessKey = "account", secretKey = "private-secret")
        `when`(connections.findById(existing.id)).thenReturn(Optional.of(existing))
        `when`(connections.save(any(StorageConnection::class.java))).thenAnswer { it.arguments[0] }
        val result = service.save(existing.id, StorageConnectionRequest("New nickname", "S3", "docs", accessKey = "account"), "admin@test.local")
        assertThat(existing.secretKey).isEqualTo("private-secret")
        assertThat(result.nickname).isEqualTo("New nickname")
        assertThat(result.secretConfigured).isTrue()
        assertThat(jacksonObjectMapper().writeValueAsString(result)).doesNotContain("private-secret")
    }

    @Test
    fun `selecting server identity explicitly clears stored credentials`() {
        val existing = StorageConnection(bucket = "docs", accessKey = "account", secretKey = "secret")
        `when`(connections.findById(existing.id)).thenReturn(Optional.of(existing))
        `when`(connections.save(any(StorageConnection::class.java))).thenAnswer { it.arguments[0] }
        service.save(existing.id, StorageConnectionRequest("Identity", "S3", "docs", useServerIdentity = true), "admin@test.local")
        assertThat(existing.accessKey).isNull()
        assertThat(existing.secretKey).isNull()
    }

    @Test
    fun `a connection with selected folders cannot be silently repointed to another bucket`() {
        val existing = StorageConnection(bucket = "original")
        `when`(connections.findById(existing.id)).thenReturn(Optional.of(existing))
        `when`(items.findAllByStorageConnectionIdOrderByTitleAsc(existing.id)).thenReturn(listOf(Item(storageConnection = existing, storagePrefix = "shared/")))
        assertThatThrownBy { service.save(existing.id, StorageConnectionRequest("Archive", "S3", "different"), "admin@test.local") }.isInstanceOf(ConflictException::class.java)
        assertThat(existing.bucket).isEqualTo("original")
        verify(connections, never()).save(any(StorageConnection::class.java))
    }

    @Test
    fun `overlapping folder selections are rejected even when the parent is currently hidden`() {
        val existing = StorageConnection(bucket = "docs")
        `when`(connections.findById(existing.id)).thenReturn(Optional.of(existing))
        `when`(items.findAllByStorageConnectionIdOrderByTitleAsc(existing.id)).thenReturn(listOf(Item(storageConnection = existing, storagePrefix = "legal/", accessible = false)))
        assertThatThrownBy { service.shareFolder(existing.id, ShareFolderRequest("Contracts", "legal/contracts/"), "admin@test.local") }.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy { service.shareFolder(existing.id, ShareFolderRequest("Everything", ""), "admin@test.local") }.isInstanceOf(ConflictException::class.java)
        verifyNoInteractions(itemService)
    }
}
