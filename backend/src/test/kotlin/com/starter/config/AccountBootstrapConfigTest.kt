package com.starter.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

class AccountBootstrapConfigTest {
    private val users = mock(UserRepository::class.java)
    private val mapper = jacksonObjectMapper()
    private val encoder = BCryptPasswordEncoder(4)
    private fun run(json: String = "") = AccountBootstrapConfig().bootstrapAccounts(mapper, users, encoder,
        BootstrapProperties().apply { bootstrapUsersJson = json }).run(DefaultApplicationArguments())
    private fun account(email: String = " OWNER@Example.com ", username: String = " owner ", role: String = " admin ") =
        mapper.writeValueAsString(listOf(BootstrapAccount(email, username, "strong-bootstrap-password", role, " Owner ", " 123 ")))

    @Test fun `no configuration creates no accounts or demo content`() {
        run()
        verifyNoInteractions(users)
    }
    @Test fun `explicit accounts are normalized and passwords are salted hashes`() {
        `when`(users.save(any(User::class.java))).thenAnswer { it.arguments[0] }
        run(account())
        val saved = ArgumentCaptor.forClass(User::class.java)
        verify(users).save(saved.capture())
        assertThat(saved.value.email).isEqualTo("owner@example.com")
        assertThat(saved.value.username).isEqualTo("owner")
        assertThat(saved.value.name).isEqualTo("Owner")
        assertThat(saved.value.contactNumber).isEqualTo("123")
        assertThat(saved.value.role).isEqualTo("ADMIN")
        assertThat(encoder.matches("strong-bootstrap-password", saved.value.password)).isTrue()
        assertThat(saved.value.canUploadDocuments).isFalse()
    }
    @Test fun `restart preserves changed passwords roles profiles and deactivation`() {
        val existing = User(email = "owner@example.com", username = "changed", password = encoder.encode("changed-user-password"), role = "USER", active = false, name = "Changed")
        `when`(users.findByEmail(existing.email)).thenReturn(Optional.of(existing))
        `when`(users.count()).thenReturn(1)
        run(account())
        verify(users, never()).save(any(User::class.java))
        assertThat(existing.active).isFalse(); assertThat(existing.role).isEqualTo("USER")
        assertThat(existing.name).isEqualTo("Changed")
        assertThat(encoder.matches("changed-user-password", existing.password)).isTrue()
    }
    @Test fun `malformed configuration fails without exposing input secrets`() {
        val secret = "private-password-value"
        assertThatThrownBy { run("[{\"password\":\"$secret\"") }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("APP_BOOTSTRAP_USERS_JSON must be a valid account array")
        verifyNoInteractions(users)
    }
    @Test fun `invalid accounts and missing initial admin fail before writes`() {
        assertThatThrownBy { run(account(role = "UNKNOWN")) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { run(account(role = "USER")) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { run(account(email = "invalid")) }.isInstanceOf(IllegalArgumentException::class.java)
        verify(users, never()).save(any(User::class.java))
    }
    @Test fun `username collisions never overwrite another identity`() {
        `when`(users.findByUsername("owner")).thenReturn(Optional.of(User(email = "other@example.com", username = "owner", password = "existing-hash")))
        assertThatThrownBy { run(account()) }.isInstanceOf(IllegalArgumentException::class.java)
        verify(users, never()).save(any(User::class.java))
    }
}
