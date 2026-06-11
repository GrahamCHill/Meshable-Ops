package com.starter.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

private val log = LoggerFactory.getLogger(SupersetService::class.java)

@Service
class SupersetService {

    @Value("\${superset.base-url:http://superset:8088}") private lateinit var baseUrl: String
    @Value("\${superset.admin-user:admin}") private lateinit var adminUser: String
    @Value("\${superset.admin-password:admin}") private lateinit var adminPassword: String

    private val rest = RestTemplate()
    private val mapper = ObjectMapper()

    fun mintGuestToken(dashboardIds: List<String>, userEmail: String, username: String): String {
        val accessToken = getAdminAccessToken()
        val csrfToken = getCsrfToken(accessToken)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
            set("X-CSRFToken", csrfToken)
            set("Referer", baseUrl)
        }

        val resources = dashboardIds.map { mapOf("type" to "dashboard", "id" to it) }
        val body = mapOf(
            "user" to mapOf("username" to username, "first_name" to username, "last_name" to ""),
            "resources" to resources,
            "rls" to emptyList<Any>()
        )

        return try {
            val response = rest.exchange(
                "$baseUrl/api/v1/security/guest_token/",
                HttpMethod.POST,
                HttpEntity(mapper.writeValueAsString(body), headers),
                String::class.java
            )
            mapper.readTree(response.body).get("token").asText()
        } catch (e: Exception) {
            log.error("Failed to mint Superset guest token: ${e.message}")
            throw RuntimeException("Could not obtain Superset guest token", e)
        }
    }

    private fun getCsrfToken(accessToken: String): String {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
            accept = listOf(MediaType.APPLICATION_JSON)
            set("Referer", baseUrl)
        }

        return try {
            val response = rest.exchange(
                "$baseUrl/api/v1/security/csrf_token/",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )
            mapper.readTree(response.body).get("result").asText()
        } catch (e: Exception) {
            log.error("Failed to obtain Superset CSRF token: ${e.message}")
            throw RuntimeException("Could not obtain Superset CSRF token", e)
        }
    }

    private fun getAdminAccessToken(): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = mapOf(
            "username" to adminUser,
            "password" to adminPassword,
            "provider" to "db",
            "refresh" to true
        )

        return try {
            val response = rest.exchange(
                "$baseUrl/api/v1/security/login",
                HttpMethod.POST,
                HttpEntity(mapper.writeValueAsString(body), headers),
                String::class.java
            )
            mapper.readTree(response.body).get("access_token").asText()
        } catch (e: Exception) {
            log.error("Failed to obtain Superset admin token: ${e.message}")
            throw RuntimeException("Could not authenticate with Superset", e)
        }
    }
}
