package com.starter.access

object Roles {
    const val SUPER_ADMIN = "SUPER_ADMIN"
    val all = setOf(SUPER_ADMIN, "ADMIN", "USER", "EXTERNAL")
    fun admin(role: String?) = role == SUPER_ADMIN || role == "ADMIN"
    // Existing plugin manifests keep their administrator/member table policies.
    fun plugin(role: String) = if (role == SUPER_ADMIN) "ADMIN" else role
}
