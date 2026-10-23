package com.starter.service

import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageConnection
import com.starter.model.postgres.User
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.repository.postgres.UserRepository
import com.starter.repository.mongo.EventRepository
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Optional

class SharedStorageAccessTest {
    private val settings = mock(AdminSettingsService::class.java)
    private val storage = FolderStorageService(settings)

    @Test
    fun `hidden folders and disabled connections deny browse download and upload before accessing storage`() {
        listOf(
            Item(storageConnection = StorageConnection(), storagePrefix = "legal/", accessible = false),
            Item(storageConnection = StorageConnection(enabled = false), storagePrefix = "legal/"),
        ).forEach { folder ->
            assertThatThrownBy { storage.browse(folder, "") }.isInstanceOf(NotFoundException::class.java)
            assertThatThrownBy { storage.download(folder, "contract.pdf", false) }.isInstanceOf(NotFoundException::class.java)
            assertThatThrownBy { storage.download(folder, "", true) }.isInstanceOf(NotFoundException::class.java)
            assertThatThrownBy { storage.upload(folder, "", listOf(StorageObjectUpload("file.txt", null, byteArrayOf()))) }.isInstanceOf(NotFoundException::class.java)
        }
        verifyNoInteractions(settings)
    }

    @Test
    fun `traversal and sibling prefixes cannot escape a shared folder or connection boundary`() {
        val folder = Item(storageConnection = StorageConnection(basePath = "contracts/"), storagePrefix = "contracts/legal/")
        listOf("../private/", "nested/../../private", "..\\private", "bad\u0000path").forEach { path ->
            assertThatThrownBy { storage.browse(folder, path) }.isInstanceOf(BadRequestException::class.java)
            assertThatThrownBy { storage.download(folder, path, false) }.isInstanceOf(BadRequestException::class.java)
        }
        folder.storagePrefix = "contracts-private/"
        assertThatThrownBy { storage.prefixFor(folder) }.isInstanceOf(BadRequestException::class.java)
    }

    @Test
    fun `same folder path in different buckets uses each connection and never creates markers`() {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${URLDecoder.decode(exchange.requestURI.toString(), StandardCharsets.UTF_8)}"
            val body = "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><KeyCount>0</KeyCount><IsTruncated>false</IsTruncated></ListBucketResult>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            listOf("legal-bucket", "finance-bucket").forEach { bucket ->
                val connection = StorageConnection(bucket = bucket, endpoint = "http://127.0.0.1:${server.address.port}", accessKey = "test", secretKey = "test", basePath = "documents/")
                storage.browse(Item(storageConnection = connection, storagePrefix = "documents/shared/"), "2026/")
            }
        } finally { server.stop(0) }
        assertThat(requests).hasSize(2)
        assertThat(requests[0]).startsWith("GET /legal-bucket?").contains("prefix=documents/shared/2026/")
        assertThat(requests[1]).startsWith("GET /finance-bucket?").contains("prefix=documents/shared/2026/")
        verifyNoInteractions(settings)
    }

    @Test
    fun `invalid upload paths reject the entire batch before its first write`() {
        val folder = Item(storageConnection = StorageConnection(bucket = "docs", endpoint = "http://127.0.0.1:1", accessKey = "test", secretKey = "test"), storagePrefix = "shared/")
        assertThatThrownBy {
            storage.upload(folder, "", listOf(StorageObjectUpload("valid.txt", null, byteArrayOf()), StorageObjectUpload("../private.txt", null, byteArrayOf())))
        }.isInstanceOf(BadRequestException::class.java)
    }

    @Test
    fun `removing an external selection only removes metadata and never deletes cloud folders`() {
        val items = mock(ItemRepository::class.java)
        val documents = mock(StorageDocumentRepository::class.java)
        val users = mock(UserRepository::class.java)
        val gateway = mock(FolderStorageService::class.java)
        val vector = mock(VectorService::class.java)
        val user = User(email = "admin@test.local")
        val folder = Item(user = user, storageConnection = StorageConnection(bucket = "docs"), storagePrefix = "legal/")
        `when`(items.findById(folder.id)).thenReturn(Optional.of(folder))
        `when`(users.findByEmail(user.email)).thenReturn(Optional.of(user))
        `when`(gateway.prefixFor(folder)).thenReturn("legal/")
        val service = ItemService(items, documents, users, mock(EventRepository::class.java), vector, gateway, mock(AuditLogService::class.java))
        service.delete(user.email, folder.id)
        verify(items).delete(folder)
        verify(vector).delete(folder.id)
        verify(gateway).prefixFor(folder)
        verifyNoMoreInteractions(gateway)
    }
}
