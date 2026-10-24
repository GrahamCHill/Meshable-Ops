package com.starter.plugins

import com.starter.access.AccessControlService
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import org.springframework.stereotype.Component

/** Service and worker checks complement HTTP routing, including document capability composition. */
@Component
class BundledModuleAccess(private val plugins: PluginRegistry, private val controls: AccessControlService) {
    fun check(module: String, actor: User, table: String, feature: String, write: Boolean = false) {
        if (feature !in controls.features(actor)) throw ForbiddenException("This feature is not available to your account")
        plugins.requireTable(module, table, actor.role, write)
    }
    fun documents(actor: User) = plugins.requireTable("documents", "generated_documents", actor.role, supporting = true)
}
