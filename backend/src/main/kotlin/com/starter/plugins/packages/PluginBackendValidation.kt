package com.starter.plugins.packages

import com.starter.exception.BadRequestException
import com.starter.plugins.PluginExtension
import com.starter.plugins.PluginRuntime
import java.net.URI

object PluginBackendValidation {
    val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
    val permissions = setOf("data.read", "data.write", "http", "settings.read")
    fun check(descriptor: PluginPackageDescriptor, files: Map<String, String>) {
        val backend = descriptor.backend
        if ((PluginExtension.API in descriptor.manifest.extensions) != (backend != null)) invalid("API extensions must declare backend modules and routes")
        if (backend == null) {
            if (descriptor.manifest.runtimes.any { it != PluginRuntime.REACT }) invalid("Declare backend modules for Kotlin or Python runtimes")
            return
        }
        if (backend.modules.isEmpty() || backend.modules.size > 4 || backend.routes.isEmpty() || backend.routes.size > 40 || backend.allowedOrigins.size > 10) invalid("Use 1–4 backend modules, 1–40 routes, and at most 10 HTTP origins")
        backend.modules.forEach { (name, module) ->
            if (!name.matches(Regex("[a-z][a-z0-9-]{0,39}")) || !module.entrypoint.startsWith("backend/") || files[module.entrypoint].isNullOrBlank() ||
                !module.entrypoint.endsWith(if (module.runtime == BackendRuntime.PYTHON) ".py" else ".jar")) invalid("Backend entrypoints must reference packaged backend/*.py or backend/*.jar files")
        }
        val declared = mutableSetOf<Pair<String,String>>()
        backend.routes.forEach { route ->
            if (!route.path.matches(Regex("/[a-z][a-z0-9-]{0,39}(/[a-z][a-z0-9-]{0,39}){0,3}")) || route.module !in backend.modules ||
                route.methods.isEmpty() || !methods.containsAll(route.methods) || route.roles.isEmpty() || !descriptor.manifest.supportedRoles.containsAll(route.roles) ||
                !permissions.containsAll(route.permissions) || "GET" in route.methods && "data.write" in route.permissions) invalid("Use declared backend modules, exact relative routes, roles, methods and supported permissions")
            route.methods.forEach { if (!declared.add(route.path to it)) invalid("Backend routes must be unique per HTTP method") }
        }
        backend.allowedOrigins.forEach { if (origin(it) != it) invalid("HTTP origins must be canonical HTTPS origins without paths, credentials or query parameters") }
        val expected = backend.modules.values.map { PluginRuntime.valueOf(it.runtime.name) }.toSet()
        if (descriptor.manifest.runtimes.isNotEmpty() && !descriptor.manifest.runtimes.containsAll(expected)) invalid("List every backend runtime in manifest.runtimes")
        if (PluginExtension.PAGE !in descriptor.manifest.extensions && PluginRuntime.REACT in descriptor.manifest.runtimes) invalid("A React runtime requires PAGE")
    }
    fun origin(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: invalid("Use an HTTPS origin")
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null || uri.port == 0 || uri.port > 65535) invalid("Use an HTTPS origin without credentials")
        val host = uri.host.lowercase()
        return "https://$host" + if (uri.port in setOf(-1, 443)) "" else ":${uri.port}"
    }
    private fun invalid(message: String): Nothing = throw BadRequestException(message)
}
