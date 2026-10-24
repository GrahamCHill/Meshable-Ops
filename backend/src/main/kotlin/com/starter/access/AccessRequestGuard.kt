package com.starter.access

import com.starter.exception.ForbiddenException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Every authenticated feature has a backend gate, independent of menus or controller role annotations. */
class AccessRequestGuard(private val access: AccessControlService) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val actor = access.actor() ?: return true
        val path = request.requestURI.removePrefix(request.contextPath).trimEnd('/')
        if (!path.startsWith("/api/") || request.method == "OPTIONS") return true
        response.setHeader("Cache-Control", "no-store")
        if (path == "/api/auth/superset-admin-access") access.requireFeature("admin.superset")
        else if (path.startsWith("/api/auth/") || path.startsWith("/api/account/") || path == "/api/access/me" ||
            path.startsWith("/api/branding") || path.startsWith("/api/shared/") || path.startsWith("/api/webhooks/incoming/")) return true
        else if (path == "/api/plugins") return true // The catalogue filters individual page permissions.
        else if (path == "/api/document-access" || path == "/api/notifications/availability") {
            if (access.features(actor).intersect(setOf("storage", "documents", "workflows")).isEmpty()) throw ForbiddenException("Document features are unavailable")
        }
        else {
            val feature = feature(path, request.method)
            if (feature == null && actor.role != Roles.SUPER_ADMIN) throw ForbiddenException("This endpoint is not available to your account")
            feature?.let(access::requireFeature)
        }
        if (actor.role == "EXTERNAL" && path.startsWith("/api/items") && request.method != "GET") throw ForbiddenException("External storage access is read only")
        return true
    }
    companion object {
        fun feature(path: String, method: String = "GET"): String? = when {
            path.startsWith("/api/admin/access-controls") -> "admin.access"
            path.startsWith("/api/admin/storage-") || path == "/api/admin/settings" || path.startsWith("/api/admin/settings/") -> "admin.storage"
            path.startsWith("/api/admin/document-access") -> "admin.documents"
            path.startsWith("/api/admin/document-templates") -> "admin.templates"
            path.startsWith("/api/admin/notifications") || path.startsWith("/api/admin/email") -> "admin.notifications"
            path.startsWith("/api/admin/audit-logs") || path.startsWith("/api/admin/backup") -> "admin.audit"
            path.startsWith("/api/admin/stack") || path == "/api/stack" || path == "/api/health" -> "admin.system"
            path.startsWith("/api/admin/") -> "admin." + path.removePrefix("/api/admin/").substringBefore('/')
            path.startsWith("/api/items") -> if (method == "GET" || path.endsWith("/upload")) "storage" else "admin.storage"
            path.startsWith("/api/storage-connections") || path == "/api/document-access" -> "storage"
            path.startsWith("/api/search") -> "search"
            path.startsWith("/api/documents") -> "documents"
            path.startsWith("/api/workflows") -> if (method in setOf("POST", "PUT") && !path.contains("/runs")) "admin.workflows" else "workflows"
            path.startsWith("/api/events") -> "events"
            path.startsWith("/api/notifications") -> "documents"
            path.startsWith("/api/plugins/") -> "plugin." + path.removePrefix("/api/plugins/").substringBefore('/')
            path == "/api/superset/dashboard-embeds" && method == "GET" || path == "/api/superset/guest-token" -> "dashboards"
            path.startsWith("/api/superset/") -> "admin.dashboards"
            else -> null
        }
    }
}

@Configuration
class AccessWebConfiguration(private val access: AccessControlService) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) { registry.addInterceptor(AccessRequestGuard(access)).addPathPatterns("/api/**") }
}
