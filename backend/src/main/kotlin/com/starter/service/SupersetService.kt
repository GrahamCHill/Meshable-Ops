package com.starter.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

private val log = LoggerFactory.getLogger(SupersetService::class.java)

@Service
class SupersetService {

    @Value("\${superset.base-url:http://superset:8088}") private lateinit var baseUrl: String
    @Value("\${superset.admin-user:admin}") private lateinit var adminUser: String
    @Value("\${superset.admin-password:admin}") private lateinit var adminPassword: String

    private val rest = RestTemplate()
    private val mapper = ObjectMapper()

    fun mintGuestToken(
        dashboardIds: List<String>,
        userEmail: String,
        username: String,
        appOrigin: String? = null,
        dashboardIdsToSync: List<String> = dashboardIds,
    ): String {
        val guestUsername = userEmail.ifBlank { username }
        val accessToken = getAdminAccessToken()
        val csrfToken = getCsrfToken(accessToken)
        syncAllowedDomains(dashboardIdsToSync, appOrigin, accessToken, csrfToken)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
            set("X-CSRFToken", csrfToken)
            set("Referer", baseUrl)
        }

        val resources = dashboardIds.map { mapOf("type" to "dashboard", "id" to it) }
        val body = mapOf(
            "user" to mapOf("username" to guestUsername, "first_name" to username, "last_name" to ""),
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

    fun resolveAppOrigin(
        originHeader: String?,
        refererHeader: String?,
        forwardedProtoHeader: String?,
        forwardedHostHeader: String?,
        hostHeader: String?,
    ): String? {
        normalizeOrigin(originHeader)?.let { return it }

        refererHeader
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    val uri = URI(it)
                    if (uri.scheme != null && uri.host != null) {
                        "${uri.scheme}://${uri.authority}"
                    } else {
                        null
                    }
                }.getOrNull()
            }
            ?.let { return normalizeOrigin(it) }

        val forwardedProto = forwardedProtoHeader?.substringBefore(',')?.trim().orEmpty()
        val forwardedHost = forwardedHostHeader?.substringBefore(',')?.trim().orEmpty()
        if (forwardedProto.isNotBlank() && forwardedHost.isNotBlank()) {
            return normalizeOrigin("$forwardedProto://$forwardedHost")
        }

        val host = hostHeader?.substringBefore(',')?.trim().orEmpty()
        if (host.isNotBlank()) {
            val scheme = if (forwardedProto.equals("https", ignoreCase = true)) "https" else "http"
            return normalizeOrigin("$scheme://$host")
        }

        return null
    }

    private fun syncAllowedDomains(
        dashboardIds: List<String>,
        appOrigin: String?,
        accessToken: String,
        csrfToken: String,
    ) {
        val normalizedOrigin = normalizeOrigin(appOrigin)
        if (normalizedOrigin == null || dashboardIds.isEmpty()) {
            return
        }

        dashboardIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter { it.all(Char::isDigit) }
            .distinct()
            .forEach { dashboardId ->
                runCatching {
                    syncAllowedDomain(dashboardId, normalizedOrigin, accessToken, csrfToken)
                }.onFailure { error ->
                    log.warn(
                        "Failed to sync Superset embed allowed_domains for dashboard {} and origin {}: {}",
                        dashboardId,
                        normalizedOrigin,
                        error.message,
                    )
                }
            }
    }

    private fun syncAllowedDomain(
        dashboardId: String,
        appOrigin: String,
        accessToken: String,
        csrfToken: String,
    ) {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
            set("X-CSRFToken", csrfToken)
            set("Referer", baseUrl)
        }

        val currentResponse = rest.exchange(
            "$baseUrl/api/v1/dashboard/$dashboardId/embedded",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )
        val resultNode = mapper.readTree(currentResponse.body).path("result")
        if (resultNode.isMissingNode || resultNode.isNull) {
            log.warn("Superset returned no embedded config payload for dashboard {}", dashboardId)
            return
        }

        val existingDomains = resultNode.path("allowed_domains")
            .takeIf { it.isArray }
            ?.mapNotNull { node -> normalizeOrigin(node.asText()) }
            .orEmpty()

        if (existingDomains.any { it.equals(appOrigin, ignoreCase = true) }) {
            return
        }

        val updatedDomains = (existingDomains + appOrigin).distinctBy(String::lowercase)
        rest.exchange(
            "$baseUrl/api/v1/dashboard/$dashboardId/embedded",
            HttpMethod.POST,
            HttpEntity(mapper.writeValueAsString(mapOf("allowed_domains" to updatedDomains)), headers),
            String::class.java
        )
        log.info("Added Superset embed origin {} to dashboard {}", appOrigin, dashboardId)
    }

    private fun normalizeOrigin(origin: String?): String? =
        origin
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    val uri = if (it.contains("://")) URI(it) else URI("https://$it")
                    if (uri.scheme == null || uri.host == null) {
                        null
                    } else {
                        "${uri.scheme.lowercase()}://${uri.authority.lowercase()}"
                    }
                }.getOrNull()
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
