package com.starter.security

import com.starter.mfa.MfaService
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.service.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils

class AuthBrowserSessionTest {
    @Test fun `issuing a session exposes no JWT in JSON and sends only protected authentication cookies`() {
        val signingKey="test-signing-key-with-at-least-thirty-two-bytes"
        val jwt=JwtService().also {ReflectionTestUtils.setField(it,"secret",signingKey)}
        val actor=User(email="member@example.test",username="member",password="hash")
        val details=mock(UserDetailsServiceImpl::class.java)
        `when`(details.loadUserByUsername(actor.email)).thenReturn(SessionUserDetails(actor,false))
        val sessions=BrowserSessions(mock(BrowserSessionRepository::class.java),jwt,SecureValues(signingKey))
        val auth=AuthService(mock(UserRepository::class.java),mock(PasswordEncoder::class.java),jwt,mock(AuthenticationManager::class.java),details,mock(MfaService::class.java),sessions)
        val response=MockHttpServletResponse()
        val result=auth.issueSession(actor,false,response)
        assertThat(result.token).isEmpty()
        assertThat(result.id).isEqualTo(actor.id.toString())
        val cookies=response.getHeaders("Set-Cookie")
        assertThat(cookies).hasSize(3)
        assertThat(cookies[0]).contains("__Host-app_auth_token=","HttpOnly","Secure","SameSite=Strict")
        assertThat(cookies[1]).contains("__Host-app_session=","HttpOnly","Secure")
    }
}
