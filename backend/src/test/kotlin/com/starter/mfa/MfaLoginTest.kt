package com.starter.mfa

import com.starter.dto.AuthRequest
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.service.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional

class MfaLoginTest {
    @Test fun `password login with enrolled factor returns challenge and clears cookie without minting session`() {
        val users = mock(UserRepository::class.java); val mfa = mock(MfaService::class.java)
        val jwt = mock(JwtService::class.java); val details = mock(UserDetailsServiceImpl::class.java)
        val manager = mock(AuthenticationManager::class.java)
        val user = User(email = "admin@example.test", password = "hash")
        `when`(users.findByEmail(user.email)).thenReturn(Optional.of(user)); `when`(mfa.enabled(user.id)).thenReturn(true)
        `when`(mfa.login(user)).thenReturn(MfaChallengeResponse(challengeToken = "opaque-challenge", methods = listOf(MfaMethod.TOTP), expiresAt = Instant.now().plusSeconds(300).toString()))
        val response = MockHttpServletResponse()
        val result = AuthService(users, mock(PasswordEncoder::class.java), jwt, manager, details, mfa).login(AuthRequest(user.email, "password"), response)
        assertThat(result).isInstanceOf(MfaChallengeResponse::class.java)
        assertThat(response.getHeader("Set-Cookie")).contains("app_auth_token=", "Max-Age=0")
        verifyNoInteractions(jwt, details)
    }
}
