package com.starter.access

import com.starter.dto.AdminCreateUserRequest
import com.starter.dto.UpdateUploadPermissionRequest
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.security.SaltedPasswordEncoder
import com.starter.service.AdminUserService
import com.starter.service.AuditLogService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class AdminRoleTest {
    private val users = mock(UserRepository::class.java)
    private val encoder = SaltedPasswordEncoder()
    private val service = AdminUserService(users, encoder, mock(AuditLogService::class.java))
    private val superAdmin = User(email = "super@example.test", role = "SUPER_ADMIN")
    private val delegated = User(email = "admin@example.test", role = "ADMIN")
    private val member = User(email = "member@example.test")
    init {
        listOf(superAdmin, delegated, member).forEach { `when`(users.findByEmail(it.email)).thenReturn(Optional.of(it)); `when`(users.lockedById(it.id)).thenReturn(Optional.of(it)) }
        `when`(users.findAll()).thenReturn(listOf(superAdmin, delegated, member))
        `when`(users.save(any(User::class.java))).thenAnswer { it.arguments[0] }
        `when`(users.saveAndFlush(any(User::class.java))).thenAnswer { it.arguments[0] }
    }
    @Test fun `delegated administrators cannot create promote or change administrator accounts`() {
        listOf("ADMIN", "SUPER_ADMIN").forEach { role -> assertThatThrownBy { service.createUser(AdminCreateUserRequest("new@example.test", "new", password = "safe-password", role = role), delegated.email) }.isInstanceOf(ForbiddenException::class.java) }
        assertThatThrownBy { service.updateRole(member.id, "SUPER_ADMIN", delegated.email) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.deactivateUser(superAdmin.id, delegated.email) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.updateUploadPermission(superAdmin.id, UpdateUploadPermissionRequest(true), delegated.email) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `super admin role changes invalidate sessions and external roles revoke uploads`() {
        member.canUploadDocuments = true
        service.updateRole(member.id, "EXTERNAL", superAdmin.email)
        assertThat(member.role).isEqualTo("EXTERNAL")
        assertThat(member.sessionVersion).isEqualTo(1)
        assertThat(member.canUploadDocuments).isFalse()
    }
    @Test fun `super admin cannot deactivate or demote own account`() {
        assertThatThrownBy { service.deactivateUser(superAdmin.id, superAdmin.email) }.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy { service.updateRole(superAdmin.id, "USER", superAdmin.email) }.isInstanceOf(ConflictException::class.java)
    }
    @Test fun `migration promotes only initial admin and records completion`() {
        val migration = mock(AccountRoleMigrationRepository::class.java)
        `when`(users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc("SUPER_ADMIN")).thenReturn(Optional.empty())
        `when`(users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc("ADMIN")).thenReturn(Optional.of(delegated))
        val runner = SuperAdminMigration(users, migration)
        runner.run(org.springframework.boot.DefaultApplicationArguments())
        assertThat(delegated.role).isEqualTo("SUPER_ADMIN")
        `when`(migration.existsById(1)).thenReturn(true)
        delegated.role = "ADMIN"
        runner.run(org.springframework.boot.DefaultApplicationArguments())
        assertThat(delegated.role).isEqualTo("ADMIN")
        verify(migration, times(1)).saveAndFlush(any())
    }
}
