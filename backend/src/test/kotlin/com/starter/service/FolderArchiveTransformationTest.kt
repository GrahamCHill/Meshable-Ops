package com.starter.service

import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageConnection
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.zip.ZipInputStream

class FolderArchiveTransformationTest {
    @Test fun `nested ZIP entries pass through the PDF transform while other files retain their bytes`() {
        val original = "original-pdf".toByteArray()
        val notes = "unchanged notes".toByteArray()
        val stamped = "stamped-pdf".toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val data = when {
                path.endsWith("agreement.pdf") -> original
                path.endsWith("notes.txt") -> notes
                else -> """<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/"><KeyCount>2</KeyCount><IsTruncated>false</IsTruncated><Contents><Key>shared/2026/nested/agreement.pdf</Key><Size>12</Size></Contents><Contents><Key>shared/2026/notes.txt</Key><Size>15</Size></Contents></ListBucketResult>""".toByteArray()
            }
            exchange.responseHeaders.add("Content-Type", if (path.endsWith(".pdf")) "application/pdf" else if (path.endsWith(".txt")) "text/plain" else "application/xml")
            exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            val folder = Item(storagePrefix = "shared/", storageConnection = StorageConnection(bucket = "docs", endpoint = "http://127.0.0.1:${server.address.port}", accessKey = "test", secretKey = "test"))
            val download = FolderStorageService(mock(AdminSettingsService::class.java)).downloadTransformed(folder, "2026/", true, PdfService.MAX_BYTES) {
                if (it.filename.endsWith(".pdf")) it.copy(bytes = stamped) else it
            }
            val files = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(download.bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) { files[entry.name] = zip.readBytes(); entry = zip.nextEntry }
            }
            assertThat(files.keys).containsExactlyInAnyOrder("nested/agreement.pdf", "notes.txt")
            assertThat(files["nested/agreement.pdf"]).isEqualTo(stamped)
            assertThat(files["notes.txt"]).isEqualTo(notes)
        } finally { server.stop(0) }
    }
}
