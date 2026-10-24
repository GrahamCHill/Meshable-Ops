package com.starter.access

object Roles {
    const val SUPER_ADMIN = "SUPER_ADMIN"
    const val BUSINESS_MANAGER = "BUSINESS_MANAGER"
    val all = setOf(SUPER_ADMIN, "ADMIN", BUSINESS_MANAGER, "USER", "EXTERNAL")
    fun admin(role: String?) = role == SUPER_ADMIN || role == "ADMIN"
    // Existing plugin manifests keep their administrator/member table policies.
    fun plugin(role: String) = when(role) { SUPER_ADMIN -> "ADMIN"; BUSINESS_MANAGER -> "USER"; else -> role }
}
