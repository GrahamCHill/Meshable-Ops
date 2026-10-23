package com.starter.controller

import com.starter.dto.*
import com.starter.service.*
import org.springframework.web.bind.annotation.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.data.mongodb.core.MongoTemplate
import org.bson.Document
import java.time.Instant

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val jdbcTemplate: JdbcTemplate,
    private val mongoTemplate: MongoTemplate,
) {
    @GetMapping
    fun health() = mapOf(
        "timestamp" to Instant.now().toString(),
        "postgres"  to checkPostgres(),
        "mongodb"   to checkMongo(),
        "pgvector"  to checkPgvector()
    )

    private fun checkPostgres() = try {
        jdbcTemplate.queryForObject("SELECT 1", Int::class.java); "UP"
    } catch (e: Exception) { "DOWN: ${e.message}" }

    private fun checkMongo() = try {
        mongoTemplate.db.runCommand(Document("ping", 1)); "UP"
    } catch (e: Exception) { "DOWN: ${e.message}" }

    private fun checkPgvector() = try {
        val version = jdbcTemplate.queryForObject(
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
            String::class.java,
        )
        "UP ($version)"
    } catch (e: Exception) { "DOWN: ${e.message}" }
}
