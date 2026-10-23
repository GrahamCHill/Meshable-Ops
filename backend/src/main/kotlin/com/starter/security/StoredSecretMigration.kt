package com.starter.security

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class StoredSecretMigration(private val jdbc: JdbcTemplate, private val encryption: SecretEncryption) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        // Identifiers are fixed application schema names, never request parameters.
        for ((table, columns) in listOf(
            "app_settings" to listOf("storage_access_key", "storage_secret_key"),
            "storage_connections" to listOf("access_key", "secret_key"),
            "ai_settings" to listOf("chat_api_key", "embedding_api_key"),
        )) {
            jdbc.queryForList("SELECT id, ${columns.joinToString()} FROM $table").forEach { row ->
                columns.forEach { column ->
                    val current = row[column] as? String
                    if (!current.isNullOrEmpty()) {
                        encryption.requireConfigured()
                        jdbc.update("UPDATE $table SET $column = ? WHERE id = ?", encryption.migrate(current), row["id"])
                    }
                }
            }
        }
    }
}
