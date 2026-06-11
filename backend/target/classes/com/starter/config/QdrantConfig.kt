package com.starter.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.*
import org.springframework.web.client.RestTemplate

private val log = LoggerFactory.getLogger(QdrantConfig::class.java)

@Configuration
class QdrantConfig {

    @Value("\${qdrant.base-url:http://qdrant:6333}") lateinit var baseUrl: String
    @Value("\${qdrant.collection-name}") lateinit var collectionName: String
    @Value("\${qdrant.vector-size}") var vectorSize: Int = 384

    @Bean
    fun qdrantRestTemplate() = RestTemplate()

    /**
     * Ensures the Qdrant collection exists on startup.
     * Called lazily by VectorService on first use to avoid startup ordering issues.
     */
    fun ensureCollection(rest: RestTemplate) {
        try {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // Check if collection already exists
            val check = rest.exchange(
                "$baseUrl/collections/$collectionName",
                HttpMethod.GET, HttpEntity<Void>(headers), String::class.java
            )
            if (check.statusCode.is2xxSuccessful) {
                log.info("Qdrant collection '$collectionName' already exists")
                return
            }
        } catch (_: Exception) { /* 404 = doesn't exist, fall through to create */ }

        try {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = """{"vectors":{"size":$vectorSize,"distance":"Cosine"}}"""
            rest.exchange(
                "$baseUrl/collections/$collectionName",
                HttpMethod.PUT, HttpEntity(body, headers), String::class.java
            )
            log.info("Qdrant collection '$collectionName' created (size=$vectorSize)")
        } catch (e: Exception) {
            log.warn("Could not create Qdrant collection: ${e.message}")
        }
    }
}
