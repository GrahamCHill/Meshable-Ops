package com.starter.access

import com.starter.repository.postgres.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Runs once per database. Subsequent starts never promote newly delegated administrators. */
@Component @Order(200)
class SuperAdminMigration(private val users: UserRepository, private val migrations: AccountRoleMigrationRepository) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (migrations.existsById(1)) return
        if (users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc(Roles.SUPER_ADMIN).isEmpty) {
            val primary = users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc("ADMIN").orElse(null) ?: return
            primary.role = Roles.SUPER_ADMIN
            users.saveAndFlush(primary)
        }
        migrations.saveAndFlush(AccountRoleMigration())
    }
}
