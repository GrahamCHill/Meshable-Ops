package com.starter.plugins

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class PluginAccessInterceptor(private val plugins: PluginRegistry, private val actors: PluginActor,private val rates:com.starter.security.RateLimits?=null) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "OPTIONS") return true
        val path = request.requestURI.removePrefix(request.contextPath)
        val id = path.removePrefix("/api/plugins/").substringBefore('/')
        val actor = actors.current(SecurityContextHolder.getContext().authentication?.name ?: "")
        plugins.requireAvailable(id, actor.role)
        if(id in com.starter.plugins.operations.OperationsStore.plugins && request.method !in setOf("GET","HEAD")) {
            rates?.check("operations-write",actor.id.toString(),120,300)
            if(path.endsWith("/refresh")||path.endsWith("/sync")||path.endsWith("/send")||path.endsWith("/poll")||id=="billing"&&path=="/api/plugins/billing")rates?.check("operations-provider",actor.id.toString(),30,3600)
        }
        return true
    }
}
