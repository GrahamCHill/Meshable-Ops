package com.starter.service

import org.bson.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootVersion
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.time.Instant

data class StackServiceInfo(
    val name: String,
    val status: String,
    val version: String? = null,
    val runtime: String? = null,
    val internalUrl: String? = null,
    val exposedOutsideVm: Boolean,
    val notes: List<String> = emptyList(),
)

data class StackInfoResponse(
    val timestamp: String,
    val publicEntrypoints: List<String>,
    val privateServices: List<String>,
    val services: List<StackServiceInfo>,
)

@Service
class StackInfoService(
    private val jdbcTemplate: JdbcTemplate,
    private val mongoTemplate: MongoTemplate,
    private val restTemplate: RestTemplate,
    @Value("\${spring.application.name}") private val applicationName: String,
    @Value("\${server.port}") private val serverPort: String,
    @Value("\${ml.base-url:http://ml-backend:8000}") private val mlBaseUrl: String,
    @Value("\${superset.base-url:http://superset:8088}") private val supersetBaseUrl: String,
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
    @Value("\${spring.datasource.url}") private val postgresJdbcUrl: String,
    @Value("\${REDIS_HOST:redis}") private val redisHost: String,
    @Value("\${REDIS_PORT:6379}") private val redisPort: Int,
) {
    fun getStackInfo() = StackInfoResponse(
        timestamp = Instant.now().toString(),
        publicEntrypoints = listOf(
            "frontend:http://localhost:3000",
            "caddy:https://your-domain",
        ),
        privateServices = listOf(
            "backend:http://backend:8080",
            "superset:http://superset:8088",
            "ml-backend:http://ml-backend:8000",
            "postgres:postgres:5432",
            "mongodb:mongodb:27017",
            "redis:redis:6379",
        ),
        services = listOf(
            backendServiceInfo(),
            frontendProxyServiceInfo(),
            supersetServiceInfo(),
            mlBackendServiceInfo(),
            postgresServiceInfo(),
            mongoServiceInfo(),
            redisServiceInfo(),
        ),
    )

    private fun backendServiceInfo() = StackServiceInfo(
        name = "Backend API",
        status = "UP",
        version = SpringBootVersion.getVersion(),
        runtime = "Java ${System.getProperty("java.version")} / Kotlin ${KotlinVersion.CURRENT}",
        internalUrl = "http://backend:$serverPort",
        exposedOutsideVm = false,
        notes = listOf(
            applicationName,
            "Only reachable through the frontend proxy inside this stack",
        ),
    )

    private fun frontendProxyServiceInfo() = StackServiceInfo(
        name = "Frontend Proxy",
        status = "UP",
        runtime = "React 18 / Vite 5 / nginx",
        internalUrl = "http://frontend:80",
        exposedOutsideVm = true,
        notes = listOf(
            "Primary public app surface",
            "Proxies backend APIs and approved Superset routes",
        ),
    )

    private fun supersetServiceInfo(): StackServiceInfo = try {
        restTemplate.exchange(
            "$supersetBaseUrl/health",
            HttpMethod.GET,
            null,
            String::class.java,
        )
        StackServiceInfo(
            name = "Apache Superset",
            status = "UP",
            internalUrl = supersetBaseUrl,
            exposedOutsideVm = false,
            notes = listOf(
                "Internal analytics service",
                "Admin authoring is exposed through /admin/superset-graph-builder only",
            ),
        )
    } catch (exc: Exception) {
        StackServiceInfo(
            name = "Apache Superset",
            status = "DOWN",
            internalUrl = supersetBaseUrl,
            exposedOutsideVm = false,
            notes = listOf(exc.message ?: "Superset health probe failed"),
        )
    }

    private fun mlBackendServiceInfo(): StackServiceInfo = try {
        val runtime = restTemplate.getForObject("$mlBaseUrl/api/v1/ml/runtime", Map::class.java)
        val python = runtime?.get("python")?.toString()
        val packages = (runtime?.get("packages") as? Map<*, *>).orEmpty()
        val numpyVersion = ((packages["numpy"] as? Map<*, *>)?.get("version"))?.toString()
        val paddleVersion = ((packages["paddle"] as? Map<*, *>)?.get("version"))?.toString()
        StackServiceInfo(
            name = "ML Backend",
            status = "UP",
            version = runtime?.get("service")?.toString(),
            runtime = python?.let { "Python $it" },
            internalUrl = mlBaseUrl,
            exposedOutsideVm = false,
            notes = listOfNotNull(
                numpyVersion?.let { "numpy $it" },
                paddleVersion?.let { "paddle $it" },
            ),
        )
    } catch (exc: Exception) {
        StackServiceInfo(
            name = "ML Backend",
            status = "DOWN",
            internalUrl = mlBaseUrl,
            exposedOutsideVm = false,
            notes = listOf(exc.message ?: "ML runtime probe failed"),
        )
    }

    private fun postgresServiceInfo(): StackServiceInfo = try {
        jdbcTemplate.queryForObject("SELECT version()", String::class.java)
        val serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String::class.java)
        val pgvectorVersion = jdbcTemplate.queryForObject(
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
            String::class.java,
        )
        StackServiceInfo(
            name = "PostgreSQL + pgvector",
            status = "UP",
            version = serverVersion,
            internalUrl = postgresJdbcUrl,
            exposedOutsideVm = false,
            notes = listOf(
                "Application data, Superset metadata, and vector index storage",
                "pgvector $pgvectorVersion",
            ),
        )
    } catch (exc: Exception) {
        StackServiceInfo(
            name = "PostgreSQL",
            status = "DOWN",
            internalUrl = postgresJdbcUrl,
            exposedOutsideVm = false,
            notes = listOf(exc.message ?: "PostgreSQL probe failed"),
        )
    }

    private fun mongoServiceInfo(): StackServiceInfo = try {
        val info = mongoTemplate.db.runCommand(Document("buildInfo", 1))
        StackServiceInfo(
            name = "MongoDB",
            status = "UP",
            version = info.getString("version"),
            internalUrl = mongoUri.substringBefore("?"),
            exposedOutsideVm = false,
            notes = listOf("Event and activity document storage"),
        )
    } catch (exc: Exception) {
        StackServiceInfo(
            name = "MongoDB",
            status = "DOWN",
            internalUrl = mongoUri.substringBefore("?"),
            exposedOutsideVm = false,
            notes = listOf(exc.message ?: "MongoDB probe failed"),
        )
    }

    private fun redisServiceInfo(): StackServiceInfo = try {
        Socket(redisHost, redisPort).use { socket ->
            socket.soTimeout = 2000
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            writer.write(redisCommand("PING"))
            writer.flush()
            val pong = reader.readLine()
            check(pong == "+PONG") { "Unexpected Redis ping response: $pong" }
        }

        StackServiceInfo(
            name = "Redis",
            status = "UP",
            version = redisInfoValue("redis_version"),
            internalUrl = "redis://$redisHost:$redisPort",
            exposedOutsideVm = false,
            notes = listOf("Superset cache and Celery coordination"),
        )
    } catch (exc: Exception) {
        StackServiceInfo(
            name = "Redis",
            status = "DOWN",
            internalUrl = "redis://$redisHost:$redisPort",
            exposedOutsideVm = false,
            notes = listOf(exc.message ?: "Redis probe failed"),
        )
    }

    private fun redisInfoValue(key: String): String? {
        Socket(redisHost, redisPort).use { socket ->
            socket.soTimeout = 2000
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            writer.write(redisCommand("INFO", "server"))
            writer.flush()

            val firstLine = reader.readLine() ?: return null
            if (!firstLine.startsWith("$")) {
                return null
            }

            val payloadLength = firstLine.drop(1).toIntOrNull() ?: return null
            val payload = CharArray(payloadLength)
            var offset = 0
            while (offset < payloadLength) {
                val read = reader.read(payload, offset, payloadLength - offset)
                if (read < 0) break
                offset += read
            }
            reader.readLine()

            return payload.concatToString()
                .lineSequence()
                .firstOrNull { it.startsWith("$key:") }
                ?.substringAfter(':')
                ?.trim()
        }
    }

    private fun redisCommand(vararg parts: String): String {
        val builder = StringBuilder("*${parts.size}\r\n")
        parts.forEach { part ->
            builder.append("\$").append(part.length).append("\r\n")
            builder.append(part).append("\r\n")
        }
        return builder.toString()
    }
}
