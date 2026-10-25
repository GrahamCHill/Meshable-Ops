package com.starter.security.oidc

import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import java.util.UUID

class ExternalIdentitiesTest {
    private val rows = mock(ExternalIdentityRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val service = ExternalIdentities(rows, users)
    private val identity = ProviderIdentity("google", "https://accounts.google.com", "immutable-subject")
    private val user = User(email = "local@example.test", username = "local", password = "hash")

    @Test fun `unknown identities cannot match or create accounts using email`() {
        `when`(rows.findById(identity.key())).thenReturn(Optional.empty())
        assertThatThrownBy { service.resolve(identity) }.isInstanceOf(ForbiddenException::class.java).hasMessageContaining("not connected")
        verifyNoInteractions(users)
        verify(rows, never()).saveAndFlush(any())
    }

    @Test fun `connected identity resolves the saved account and rejects deactivation`() {
        `when`(rows.findById(identity.key())).thenReturn(Optional.of(ExternalIdentity(identity.key(), user.id, identity.provider, identity.issuer, identity.subject)))
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        assertThat(service.resolve(identity)).isSameAs(user)
        user.active = false
        assertThatThrownBy { service.resolve(identity) }.isInstanceOf(ForbiddenException::class.java)
    }

    @Test fun `identity keys separate providers and issuers even when subjects match`() {
        assertThat(identity.key()).isNotEqualTo(identity.copy(provider = "entra").key())
            .isNotEqualTo(identity.copy(issuer = "https://other.example.test").key())
    }

    @Test fun `connecting requires an active account at the original security version`() {
        `when`(users.lockedById(user.id)).thenReturn(Optional.of(user))
        assertThatThrownBy { service.connect(identity, user.id, 1) }.isInstanceOf(ForbiddenException::class.java)
        user.active = false
        assertThatThrownBy { service.connect(identity, user.id, 0) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(rows)
    }

    @Test fun `a provider identity cannot be stolen from another workspace account`() {
        `when`(users.lockedById(user.id)).thenReturn(Optional.of(user))
        `when`(rows.findById(identity.key())).thenReturn(Optional.of(ExternalIdentity(identity.key(), UUID.randomUUID(), identity.provider, identity.issuer, identity.subject)))
        assertThatThrownBy { service.connect(identity, user.id, 0) }.isInstanceOf(ConflictException::class.java)
        verify(rows, never()).saveAndFlush(any())
    }

    @Test fun `a provider connection must be disconnected before it can be replaced`() {
        `when`(users.lockedById(user.id)).thenReturn(Optional.of(user))
        `when`(rows.findById(identity.key())).thenReturn(Optional.empty())
        `when`(rows.findByUserIdAndProvider(user.id, "google")).thenReturn(ExternalIdentity("previous-key", user.id, "google"))
        assertThatThrownBy { service.connect(identity, user.id, 0) }.isInstanceOf(ConflictException::class.java)
        verify(rows, never()).saveAndFlush(any())
    }

    @Test fun `explicit connections are saved without modifying local roles or passwords`() {
        `when`(users.lockedById(user.id)).thenReturn(Optional.of(user))
        `when`(rows.findById(identity.key())).thenReturn(Optional.empty())
        service.connect(identity, user.id, 0)
        val captor = org.mockito.ArgumentCaptor.forClass(ExternalIdentity::class.java)
        verify(rows).saveAndFlush(captor.capture())
        assertThat(captor.value.userId).isEqualTo(user.id)
        assertThat(captor.value.subject).isEqualTo(identity.subject)
        verify(users, never()).save(any())
    }
}
