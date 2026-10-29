package com.starter.plugins.workflows

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/** Routes are discovered from installed core/plugin controllers rather than a fixed list of UI buttons. */
@Component
class WorkflowActionCatalogue(@Qualifier("requestMappingHandlerMapping") private val mappings: ObjectProvider<RequestMappingHandlerMapping>,
    private val providers: List<WorkflowActionContextProvider> = emptyList()) {
    fun all(): List<WorkflowActionDescriptor> {
        val routes = mappings.getObject().handlerMethods.flatMap { (mapping, handler) ->
            mapping.patternValues.filter(::eligible).flatMap { route ->
                mapping.methodsCondition.methods.ifEmpty { setOf(org.springframework.web.bind.annotation.RequestMethod.GET) }.map { method ->
                    WorkflowActionDescriptor("route:$method $route", "${handler.method.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")} · $method $route",
                        plugin(route), external(route), method.name, route)
                }
            }
        }
        val groups = (routes + providers.flatMap { it.actions }).groupBy { it.pluginId }.map { (plugin,actions) ->
            WorkflowActionDescriptor("plugin:$plugin", "All $plugin actions", plugin,fields=actions.flatMap { it.fields }.distinct()) }
        return (listOf(WorkflowActionDescriptor("category:external", "All external deliveries and exports", "system", true)) + groups + routes +
            providers.flatMap { it.actions }).distinctBy { it.id }.sortedWith(compareBy({ it.pluginId }, { it.label }))
    }
    fun eligible(path: String): Boolean {
        if (path.startsWith("/api/workflows/") && external(path)) return true
        return path.startsWith("/api/") && listOf("/api/auth", "/api/account", "/api/access", "/api/branding", "/api/workflows", "/api/admin/workflows", "/api/webhooks/incoming", "/api/shared").none { path == it || path.startsWith("$it/") }
    }
    fun plugin(path: String): String = when {
        path.startsWith("/api/plugins/") -> path.removePrefix("/api/plugins/").substringBefore('/').takeUnless { '{' in it } ?: "plugins"
        path.startsWith("/api/admin/") -> path.removePrefix("/api/admin/").substringBefore('/')
        else -> path.removePrefix("/api/").substringBefore('/')
    }
    fun external(path: String) = Regex("(?i)(^|/)(send|email|download|export|exports|share|deliver|pdf)(/|$)").containsMatchIn(path)
}
