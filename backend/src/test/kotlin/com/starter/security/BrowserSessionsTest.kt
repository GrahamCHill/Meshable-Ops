package com.starter.security

import com.starter.service.JwtService
import com.starter.model.postgres.User
import com.starter.config.JwtAuthFilter
import com.starter.service.UserDetailsServiceImpl
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.*
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BrowserSessionsTest {
    private val secret="test-signing-secret-at-least-thirty-two-bytes-long"
    private val jwt=JwtService().also {ReflectionTestUtils.setField(it,"secret",secret)}
    private val records=mutableMapOf<UUID,BrowserSession>()
    private val rows=mock(BrowserSessionRepository::class.java) {call->when(call.method.name){
        "saveAndFlush"->(call.arguments[0] as BrowserSession).also {records[it.id]=it}
        "findById"->Optional.ofNullable(records[call.arguments[0]])
        else->RETURNS_DEFAULTS.answer(call)
    }}
    private val service=BrowserSessions(rows,jwt,SecureValues(secret))
    private val account=User(email="member@example.test",password="hash")
    private fun token(context:BrowserSessionContext)=jwt.generateToken(SessionUserDetails(account,false),mapOf("sid" to context.id.toString(),"fp" to SecureValues.hash(context.fingerprint)))
    @Test fun `a copied JWT alone cannot authenticate without the separate browser secret`() {
        val context=service.create(account.id,60000);val token=token(context)
        val copied=MockHttpServletRequest("GET","/api/projects");copied.addHeader("Authorization","Bearer $token")
        assertThat(service.valid(token,copied)).isFalse()
        copied.setCookies(Cookie("app_session","different-browser"))
        assertThat(service.valid(token,copied)).isFalse()
        copied.setCookies(Cookie("__Host-app_session",context.fingerprint))
        assertThat(service.valid(token,copied)).isTrue()
        assertThat(records[context.id]!!.contextHash).doesNotContain(context.fingerprint)
        assertThat(service.valid(jwt.generateToken(SessionUserDetails(account,false)),copied)).isFalse()
    }
    @Test fun `writes require CSRF proof and revoked or expired sessions cannot be replayed`() {
        val context=service.create(account.id,60000);val token=token(context)
        val request=MockHttpServletRequest("POST","/api/projects");request.setCookies(Cookie("app_session",context.fingerprint),Cookie("app_auth_token",token))
        assertThat(service.valid(token,request)).isFalse()
        service.revoke(request);assertThat(records[context.id]!!.revokedAt).isNull()
        request.addHeader("X-XSRF-TOKEN",context.csrf)
        assertThat(service.valid(token,request)).isTrue()
        service.revoke(request);assertThat(service.valid(token,request)).isFalse()
        val expired=service.create(account.id,-1);request.setCookies(Cookie("app_session",expired.fingerprint))
        assertThat(service.valid(token(expired),request)).isFalse()
    }
    @Test fun `session and proof cookies are protected and the CSRF cookie contains no credentials`() {
        val context=service.create(account.id,60000);val response=MockHttpServletResponse()
        service.write(response,token(context),context,true,60000)
        val headers=response.getHeaders("Set-Cookie")
        assertThat(headers).hasSize(3)
        assertThat(headers.first()).contains("__Host-app_auth_token=","Secure","HttpOnly","SameSite=Strict","Path=/")
        assertThat(headers[1]).contains("__Host-app_session=","HttpOnly")
        assertThat(headers[2]).contains("__Host-XSRF-TOKEN=").doesNotContain("HttpOnly",token(context),context.fingerprint)
    }
    @Test fun `cross-site login and write requests stop before credentials or session storage are read`() {
        val users=mock(UserDetailsServiceImpl::class.java);val sessions=mock(BrowserSessions::class.java)
        val filter=JwtAuthFilter(jwt,users,sessions)
        val request=MockHttpServletRequest("POST","/api/auth/login");request.addHeader("Origin","https://attacker.example");request.addHeader("Sec-Fetch-Site","cross-site")
        val response=MockHttpServletResponse();var reached=false
        filter.doFilter(request,response,jakarta.servlet.FilterChain {_,_->reached=true})
        assertThat(response.status).isEqualTo(403);assertThat(reached).isFalse();verifyNoInteractions(users,sessions)
    }
}
