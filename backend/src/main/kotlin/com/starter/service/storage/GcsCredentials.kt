package com.starter.service.storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.auth.oauth2.ServiceAccountCredentials
import com.starter.exception.BadRequestException

internal fun gcsServiceAccountCredentials(json: String): ServiceAccountCredentials = try {
    val fields = jacksonObjectMapper().readTree(json)
    require(fields.path("type").asText() == "service_account")
    // Only accept service account keys for Google's token service. Other ADC file types
    // can execute credential helpers and belong in the server's trusted configuration.
    require(fields.path("token_uri").asText() in setOf("https://oauth2.googleapis.com/token", "https://accounts.google.com/o/oauth2/token"))
    require(fields.path("universe_domain").asText("googleapis.com") == "googleapis.com")
    json.byteInputStream().use { ServiceAccountCredentials.fromStream(it) }
} catch (ex: Exception) {
    throw BadRequestException("Enter valid Google service account JSON with a private key and a Google OAuth token URI")
}
