package com.starter.share

import com.starter.exception.BadRequestException
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

@Service
class ShareSettingsService(private val repository: FileShareSettingsRepository, private val notifications: NotificationService,
    @Value("\${app.public-base-url:}") private val defaultBaseUrl: String) {
    fun settings(): ShareSettingsView {
        val saved = repository.findById(1).orElseGet { FileShareSettings() }
        return ShareSettingsView(saved.enabled, saved.publicBaseUrl.ifBlank { defaultBaseUrl }.trimEnd('/'), saved.maxLifetimeHours, notifications.availableFor("shares.email","email"))
    }
    fun save(request: ShareSettingsRequest): ShareSettingsView {
        val url = request.publicBaseUrl.trim().trimEnd('/')
        if (url.isNotBlank()) validate(url)
        val saved = repository.findById(1).orElseGet { FileShareSettings() }
        saved.enabled = request.enabled; saved.publicBaseUrl = url; saved.maxLifetimeHours = request.maxLifetimeHours
        repository.saveAndFlush(saved); return settings()
    }
    fun baseUrl(): String = settings().publicBaseUrl.also { validate(it) }
    private fun validate(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri == null || uri.host == null || uri.userInfo != null || uri.query != null || uri.fragment != null || uri.path.orEmpty().isNotBlank() ||
            uri.scheme !in setOf("https", "http") || uri.scheme == "http" && uri.host !in setOf("localhost", "127.0.0.1", "::1", "[::1]"))
            throw BadRequestException("Configure the public app origin as HTTPS, without a path or credentials (HTTP is allowed for loopback development)")
    }
}
