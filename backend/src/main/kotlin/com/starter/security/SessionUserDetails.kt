package com.starter.security

import com.starter.model.postgres.User
import org.springframework.security.core.authority.SimpleGrantedAuthority

class SessionUserDetails(account: User, val mfaEnabled: Boolean) : org.springframework.security.core.userdetails.User(
    account.email, account.password, account.active, true, true, true, (if (account.role == "SUPER_ADMIN") listOf("SUPER_ADMIN", "ADMIN") else listOf(account.role)).map { SimpleGrantedAuthority("ROLE_$it") }) {
    val sessionVersion = account.sessionVersion
}
