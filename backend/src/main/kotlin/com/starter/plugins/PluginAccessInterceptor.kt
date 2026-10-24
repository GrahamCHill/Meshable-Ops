package com.starter.plugins

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class PluginAccessInterceptor(private val plugins: PluginRegistry, private val actors: PluginActor) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "OPTIONS") return true
        val path = request.requestURI.removePrefix(request.contextPath)
        val id = path.removePrefix("/api/plugins/").substringBefore('/')
        val actor = actors.current(SecurityContextHolder.getContext().authentication?.name ?: "")
        plugins.requireAvailable(id, actor.role)
        return true
    }
}
