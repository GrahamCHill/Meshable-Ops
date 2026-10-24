package com.starter.plugins

import com.starter.exception.ForbiddenException
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Component

@Component
class PluginActor(private val users: UserRepository) {
    @org.springframework.transaction.annotation.Transactional(readOnly=true,propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    fun fresh(email: String) = current(email)
    fun current(email: String) = users.findByEmail(email).orElse(null)?.takeIf { it.active }
        ?: throw ForbiddenException("An active account is required")
}
