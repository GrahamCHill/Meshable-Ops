package com.starter.plugins

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/** Legacy first-class APIs have exactly the same lifecycle gate as /api/plugins/{id}. */
@Component
class BundledModuleGuard(private val plugins: PluginRegistry, private val actors: PluginActor) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "OPTIONS") return true
        val path = request.requestURI.removePrefix(request.contextPath).trimEnd('/')
        val module = owner(path) ?: return true
        val actor = actors.current(org.springframework.security.core.context.SecurityContextHolder.getContext().authentication?.name ?: "")
        plugins.requireAvailable(module, actor.role)
        return true
    }
    companion object {
        fun owner(path: String): String? = when {
            listOf("/api/documents", "/api/admin/document-templates").any { path == it || path.startsWith("$it/") } -> "documents"
            listOf("/api/workflows", "/api/admin/workflows").any { path == it || path.startsWith("$it/") } -> "workflows"
            else -> null
        }
    }
}
