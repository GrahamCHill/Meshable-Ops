package com.starter.security

import com.starter.service.JwtService
import jakarta.persistence.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity @Table(name="user_browser_sessions",indexes=[Index(columnList="user_id,expires_at")])
class BrowserSession(@Id val id: UUID = UUID.randomUUID(),val userId: UUID = UUID.randomUUID(),val contextHash: String = "",val csrfHash: String = "",
    val expiresAt: Instant = Instant.now(),var revokedAt: Instant? = null)
interface BrowserSessionRepository: JpaRepository<BrowserSession,UUID> {fun deleteByExpiresAtBefore(time: Instant): Long}
data class BrowserSessionContext(val id: UUID,val fingerprint: String,val csrf: String) {override fun toString()="BrowserSessionContext(id=$id)"}

@Service
class BrowserSessions(private val rows: BrowserSessionRepository,private val jwt: JwtService,private val values: SecureValues) {
    @Transactional fun create(userId: UUID,lifetimeMs: Long): BrowserSessionContext {
        rows.deleteByExpiresAtBefore(Instant.now())
        val context=BrowserSessionContext(UUID.randomUUID(),values.token(),values.token())
        rows.saveAndFlush(BrowserSession(context.id,userId,SecureValues.hash(context.fingerprint),SecureValues.hash(context.csrf),Instant.now().plusMillis(lifetimeMs)))
        return context
    }
    fun valid(token: String,request: HttpServletRequest): Boolean {
        val (id,hash)=jwt.browserContext(token) ?: return false
        val record=rows.findById(id).orElse(null) ?: return false
        val proof=cookie(request,"app_session") ?: return false
        if(record.revokedAt!=null || record.expiresAt<=Instant.now() || !same(record.contextHash,hash) || !same(record.contextHash,SecureValues.hash(proof)))return false
        if(request.method !in setOf("GET","HEAD","OPTIONS")) {
            val csrf=request.getHeader("X-XSRF-TOKEN") ?: return false
            if(!same(record.csrfHash,SecureValues.hash(csrf)))return false
        }
        return true
    }
    @Transactional fun revoke(request: HttpServletRequest) {
        val token=authToken(request) ?: return
        val context=runCatching {jwt.browserContext(token)}.getOrNull() ?: return
        val record=rows.findById(context.first).orElse(null) ?: return
        val proof=cookie(request,"app_session") ?: return
        if(same(record.contextHash,SecureValues.hash(proof))){record.revokedAt=Instant.now();rows.saveAndFlush(record)}
    }
    fun write(response: HttpServletResponse,token: String,context: BrowserSessionContext,secure: Boolean,lifetimeMs: Long) {
        writeCookie(response,"app_auth_token",token,secure,true,lifetimeMs)
        writeCookie(response,"app_session",context.fingerprint,secure,true,lifetimeMs)
        writeCookie(response,"XSRF-TOKEN",context.csrf,secure,false,lifetimeMs)
    }
    fun clear(response: HttpServletResponse,secure: Boolean) {
        for(name in listOf("app_auth_token","app_session","XSRF-TOKEN")) {
            writeCookie(response,name,"",secure,name!="XSRF-TOKEN",0)
            // Expire the development cookie too when upgrading an installation to secure cookies.
            if(secure)writeCookie(response,name,"",false,name!="XSRF-TOKEN",0)
        }
    }
    private fun writeCookie(response: HttpServletResponse,name: String,value: String,secure: Boolean,httpOnly: Boolean,lifetimeMs: Long) {
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(if(secure)"__Host-$name" else name,value).httpOnly(httpOnly).secure(secure).sameSite("Strict").path("/").maxAge(lifetimeMs/1000).build().toString())
    }
    companion object {
        fun cookie(request: HttpServletRequest,name: String): String? = request.cookies?.firstOrNull {it.name=="__Host-$name"}?.value
            ?: request.cookies?.firstOrNull {it.name==name}?.value
        fun authToken(request: HttpServletRequest): String? = cookie(request,"app_auth_token")?.takeIf {it.isNotBlank()}
            ?: request.getHeader("Authorization")?.takeIf {it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        fun same(first: String,second: String)=java.security.MessageDigest.isEqual(first.toByteArray(Charsets.UTF_8),second.toByteArray(Charsets.UTF_8))
    }
}
