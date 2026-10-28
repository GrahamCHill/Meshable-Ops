package com.starter.service.storage

import com.starter.model.postgres.AppSettings
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Real SDK/HTTP pagination without remote cloud latency or credentials. */
internal class StorageListingFixture(val fileCount: Int = 2500) : AutoCloseable {
    val requests = CopyOnWriteArrayList<String>()
    val writes = AtomicInteger()
    @Volatile var version = 1
    @Volatile var status = 200
    @Volatile var rootSegment = "a"
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val settings: AppSettings get() = AppSettings(storageProvider = "S3", storageBucket = "docs",
        storageRootPath = "folders", storageEndpoint = "http://127.0.0.1:${server.address.port}",
        storageAccessKey = "fixture", storageSecretKey = "fixture")

    init {
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI}"
            if (exchange.requestMethod != "GET") writes.incrementAndGet()
            val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate {
                URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8) to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8)
            }
            val prefix = query["prefix"].orEmpty()
            val delimiter = query["delimiter"]
            val start = query["continuation-token"]?.toInt() ?: 0
            val objects = buildList {
                add("folders/$rootSegment/")
                add("folders/$rootSegment/empty/")
                repeat(fileCount) { add("folders/$rootSegment/v$version-file-${it.toString().padStart(5, '0')}.txt") }
                add("folders/$rootSegment/nested/note.txt")
                add("folders/$rootSegment/__generated_pdfs__/export.pdf")
                add("folders/b/other.txt")
            }.filter { it.startsWith(prefix) }
            val children = if (delimiter == null) emptyList() else objects.mapNotNull { key ->
                key.removePrefix(prefix).takeIf { '/' in it }?.substringBefore('/')?.let { "$prefix$it/" }
            }.distinct().sorted()
            val files = if (delimiter == null) objects else objects.filter { '/' !in it.removePrefix(prefix) }
            // Count both common prefixes and objects towards the S3 page limit.
            val all = children.map { true to it } + files.map { false to it }
            val page = all.drop(start).take(1000)
            val more = start + page.size < all.size
            val body = (if (status != 200) "<Error><Code>AccessDenied</Code><Message>fixture</Message></Error>" else buildString {
                append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><IsTruncated>$more</IsTruncated>")
                if (more) append("<NextContinuationToken>${start + page.size}</NextContinuationToken>")
                page.forEach { (folder, key) ->
                    if (folder) append("<CommonPrefixes><Prefix>$key</Prefix></CommonPrefixes>")
                    else append("<Contents><Key>$key</Key><Size>12</Size><LastModified>2026-10-28T00:00:00Z</LastModified></Contents>")
                }
                append("</ListBucketResult>")
            }).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }
    override fun close() { server.stop(0) }
}
