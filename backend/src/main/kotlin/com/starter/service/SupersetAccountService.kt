package com.starter.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.JsonProcessingException
import com.starter.access.AccessControlService
import com.starter.access.Roles
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.security.RateLimits
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

data class SupersetAccountResponse(val username: String, val loginPath: String = "/login/", val created: Boolean)

/** Only the current app account can provision/reset its personal analytics login. */
@Service
class SupersetAccountService(
    private val access: AccessControlService,
    private val rates: RateLimits,
    private val audit: AuditLogService,
    private val mapper: ObjectMapper,
    builder: RestTemplateBuilder,
    @Value("\${superset.base-url:http://superset:8088}") private val baseUrl: String,
    @Value("\${superset.admin-user:admin}") private val adminUser: String,
    @Value("\${superset.admin-password}") private val adminPassword: String,
) {
    private val rest = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(20)).build()
    private val log = LoggerFactory.getLogger(javaClass)

    fun setPassword(password: String): SupersetAccountResponse {
        val actor = access.actor() ?: throw ForbiddenException("Authentication required")
        if (!Roles.admin(actor.role) || !actor.active) throw ForbiddenException("Administrator access required")
        access.requireFeature("admin.superset")
        if (password.length !in 14..128 || password.isBlank()) {
            throw BadRequestException("Choose a Superset password between 14 and 128 characters")
        }
        val email = actor.email.trim().lowercase()
        if (email.isBlank() || email.length > 250 || email.equals(adminUser, ignoreCase = true)) {
            throw ConflictException("This email cannot be used for a personal Superset account. Contact your Superset administrator.")
        }
        rates.check("superset-password", actor.id.toString(), 5, 300)

        try {
            val token = request("/api/v1/security/login", HttpMethod.POST, HttpHeaders(), mapOf(
                "username" to adminUser, "password" to adminPassword, "provider" to "db", "refresh" to false,
            )).path("access_token").asText().takeIf { it.isNotBlank() }
                ?: throw BadRequestException("Could not authenticate the Superset service account. Contact your administrator.")
            val headers = HttpHeaders().apply { setBearerAuth(token); set("Referer", baseUrl) }

            // Search both unique identity fields before touching an existing account.
            val matches = (find("users", "username", email, headers) + find("users", "email", email, headers))
                .distinctBy { it.path("id").asInt() }
            val existing = matches.singleOrNull()
            if (matches.isNotEmpty() && (existing == null ||
                    existing.path("username").asText() != email || existing.path("email").asText() != email ||
                    !existing.path("active").asBoolean() ||
                    existing.path("roles").map { it.path("name").asText() } != listOf("Alpha"))) {
                throw ConflictException("This email is already assigned to another Superset account or its access has changed. Contact your Superset administrator.")
            }
            val alpha = if (existing == null) find("roles", "name", "Alpha", headers).singleOrNull()
                ?: throw BadRequestException("Superset's authoring role is unavailable. Ask your administrator to initialize Superset.") else null
            val csrf = request("/api/v1/security/csrf_token/", HttpMethod.GET, headers).path("result").asText()
            headers.set("X-CSRFToken", csrf)

            if (existing == null) {
                val name = actor.name?.trim().orEmpty().ifBlank { actor.username }.ifBlank { email.substringBefore('@') }
                request("/api/v1/security/users/", HttpMethod.POST, headers, mapOf(
                    "username" to email, "email" to email, "password" to password, "active" to true,
                    "first_name" to name.substringBefore(' ').take(64),
                    "last_name" to name.substringAfter(' ', "").take(64),
                    "roles" to listOf(alpha!!.path("id").asInt()),
                ))
            } else {
                // No role/profile mutation, and ownership stays attached to the same Superset user id.
                request("/api/v1/security/users/${existing.path("id").asInt()}", HttpMethod.PUT, headers, mapOf("password" to password))
            }
            audit.record(actor.email, if (existing == null) "SUPERSET_ACCOUNT_CREATED" else "SUPERSET_PASSWORD_CHANGED", "USER", actor.id.toString())
            return SupersetAccountResponse(email, created = existing == null)
        } catch (error: RestClientResponseException) {
            // Superset's user-update API can echo passwords. Never propagate/log response bodies or exceptions.
            log.warn("Superset account request failed with HTTP {}", error.statusCode.value())
            throw BadRequestException(if (error.statusCode.value() in setOf(400, 422))
                "Superset could not save this account. Check its password policy or ask your Superset administrator to check for an existing account."
                else "Superset account setup is unavailable. Ask your administrator to check Superset and its user-management API.")
        } catch (_: RestClientException) {
            throw BadRequestException("Could not reach Superset. Try again shortly.")
        } catch (_: JsonProcessingException) {
            throw BadRequestException("Superset returned an invalid account response. Contact your Superset administrator.")
        }
    }

    private fun find(resource: String, column: String, value: String, headers: HttpHeaders): List<JsonNode> {
        // Rison avoids FAB's JSON query fallback decoding '+' in email addresses as spaces.
        val quoted = "'" + value.replace("!", "!!").replace("'", "!'") + "'"
        val query = "(filters:!((col:$column,opr:eq,value:$quoted)),page_size:2)"
        val uri = UriComponentsBuilder.fromHttpUrl(baseUrl.trimEnd('/') + "/api/v1/security/$resource/")
            .queryParam("q", "{query}").encode().buildAndExpand(query).toUri()
        val response = rest.exchange(uri, HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)
        val result = mapper.readTree(response.body)
        if (!result.path("result").isArray || result.path("count").asInt() > 1) {
            throw ConflictException("Superset returned an ambiguous account match. Contact your Superset administrator.")
        }
        return result.path("result").toList()
    }

    private fun request(path: String, method: HttpMethod, headers: HttpHeaders, body: Any? = null): JsonNode {
        val jsonHeaders = HttpHeaders().apply { putAll(headers); contentType = MediaType.APPLICATION_JSON; accept = listOf(MediaType.APPLICATION_JSON) }
        val response = rest.exchange(baseUrl.trimEnd('/') + path, method,
            HttpEntity(body?.let(mapper::writeValueAsString), jsonHeaders), String::class.java)
        return mapper.readTree(response.body)
    }
}
