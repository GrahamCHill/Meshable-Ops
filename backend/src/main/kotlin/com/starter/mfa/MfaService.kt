package com.starter.mfa

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.SendEmailRequest
import com.starter.email.EmailDeliveryException
import com.starter.exception.BadRequestException
import com.starter.model.postgres.User
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import com.starter.repository.postgres.UserRepository
import com.starter.security.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

@Service
class MfaService(
    private val users: UserRepository,
    private val credentials: MfaCredentialRepository,
    private val challenges: MfaChallengeRepository,
    private val passwords: PasswordEncoder,
    private val tokens: SecureValues,
    private val encryption: SecretEncryption,
    private val notifications: NotificationService,
    private val mapper: ObjectMapper,
    private val rates: RateLimits,
    private val emailGuard: EmailMfaGuard,
) {
    @Value("\${app.mfa.primary-admin-email:admin@app.local}") private var primaryEmail: String = "admin@app.local"
    fun recommended(user: User): Boolean {
        if (!com.starter.access.Roles.admin(user.role) || user.mfaPromptDismissed || credentials.enabledCount(user.id) > 0) return false
        val primary = users.findByEmail(primaryEmail.lowercase()).orElse(null)?.takeIf { com.starter.access.Roles.admin(it.role) && it.active }
            ?: users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc("SUPER_ADMIN").orElse(null)
            ?: users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc("ADMIN").orElse(null)
        return primary?.id == user.id
    }
    fun enabled(id: UUID) = credentials.enabledCount(id) > 0
    fun status(email: String): MfaStatus {
        val user = user(email); val saved = credentials.findById(user.id).orElse(null)
        return MfaStatus(saved?.totpSecret != null, saved?.emailEnabled == true, notifications.availableOtp("email"),
            encryption.configured, saved?.let { recovery(it).size } ?: 0, recommended(user))
    }
    @Transactional fun dismiss(email: String) { val user = user(email); user.mfaPromptDismissed = true; users.save(user) }

    @Transactional
    fun login(user: User): MfaChallengeResponse {
        val saved = credentials.findById(user.id).orElse(null) ?: invalid()
        rates.check("mfa-login-account", user.id.toString(), 12, 900)
        val (raw, challenge) = create(user, ChallengePurpose.LOGIN)
        val methods = buildList {
            if (saved.totpSecret != null) add(MfaMethod.TOTP)
            if (saved.emailEnabled && notifications.availableOtp("email")) add(MfaMethod.EMAIL)
            if (recovery(saved).isNotEmpty()) add(MfaMethod.RECOVERY)
        }
        return MfaChallengeResponse(challengeToken = raw, methods = methods, expiresAt = challenge.expiresAt.toString())
    }

    @Transactional(noRollbackFor = [BadCredentialsException::class, TooManyRequestsException::class])
    fun verifyLogin(request: MfaVerifyRequest): User {
        val candidate = challenges.findById(SecureValues.hash(request.challengeToken)).orElse(null) ?: invalid()
        val user = users.lockedById(candidate.userId).orElse(null)?.takeIf { it.active } ?: invalid()
        val challenge = challenge(request.challengeToken, ChallengePurpose.LOGIN)
        rates.check("mfa-verify-account", challenge.userId.toString(), 10, 900)
        val saved = credentials.locked(challenge.userId).orElse(null) ?: invalid()
        if (!saved.enabled()) invalid()
        if (!verify(saved, request.method, request.code, challenge.takeIf { request.method == MfaMethod.EMAIL })) fail(challenge)
        challenge.consumed = true; challenges.saveAndFlush(challenge); credentials.saveAndFlush(saved)
        return user
    }

    @Transactional
    fun emailCode(raw: String) {
        val candidate = challenges.findById(SecureValues.hash(raw)).orElse(null) ?: invalid()
        users.lockedById(candidate.userId).orElse(null)?.takeIf { it.active } ?: invalid()
        val challenge = challenge(raw, ChallengePurpose.LOGIN)
        val saved = credentials.findById(challenge.userId).orElse(null) ?: invalid()
        if (!saved.emailEnabled) invalid()
        sendCode(challenge)
    }

    @Transactional(noRollbackFor = [BadCredentialsException::class, TooManyRequestsException::class])
    fun begin(email: String, kind: ChallengePurpose, request: MfaSetupRequest): MfaSetupResponse {
        val user = user(email, true); checkPassword(user, request.password)
        val saved = credentials.locked(user.id).orElseGet { MfaCredential(user.id) }
        if (kind == ChallengePurpose.MANAGE_EMAIL) {
            if (!saved.emailEnabled) throw BadRequestException("Email MFA is not enabled")
        } else {
            if (saved.enabled()) {
                val method = request.method ?: throw BadRequestException("Verify an existing MFA method or recovery code first")
                checkProof(saved, method, request.code, request.challengeToken)
            }
            if (kind == ChallengePurpose.SETUP_TOTP && saved.totpSecret != null || kind == ChallengePurpose.SETUP_EMAIL && saved.emailEnabled)
                throw BadRequestException("This MFA method is already enabled")
        }
        if (kind == ChallengePurpose.SETUP_TOTP) encryption.requireConfigured()
        val (raw, challenge) = create(user, kind)
        if (kind == ChallengePurpose.SETUP_TOTP) challenge.pendingSecret = Totp.secret() else sendCode(challenge)
        challenges.saveAndFlush(challenge); credentials.saveAndFlush(saved)
        val secret = challenge.pendingSecret
        val label = URLEncoder.encode("WeaveOps:${user.email}", Charsets.UTF_8).replace("+", "%20")
        return MfaSetupResponse(raw, challenge.expiresAt.toString(), secret,
            secret?.let { "otpauth://totp/$label?secret=$it&issuer=WeaveOps&algorithm=SHA1&digits=6&period=30" })
    }

    @Transactional(noRollbackFor = [BadCredentialsException::class, TooManyRequestsException::class])
    fun confirm(email: String, request: MfaVerifyRequest): Pair<User, List<String>> {
        val user = user(email, true)
        val challenge = challenges.locked(SecureValues.hash(request.challengeToken)).orElse(null) ?: invalid()
        validateChallenge(challenge)
        if (challenge.userId != user.id || challenge.purpose !in setOf(ChallengePurpose.SETUP_TOTP, ChallengePurpose.SETUP_EMAIL)) invalid()
        rates.check("mfa-confirm-account", user.id.toString(), 10, 900)
        val saved = credentials.locked(user.id).orElseGet { MfaCredential(user.id) }
        val first = !saved.enabled()
        if (challenge.purpose == ChallengePurpose.SETUP_TOTP) {
            if (request.method != MfaMethod.TOTP || saved.totpSecret != null) invalid()
            val step = challenge.pendingSecret?.let { Totp.verify(it, request.code, -1, Instant.now().epochSecond) } ?: run { fail(challenge) }
            saved.totpSecret = challenge.pendingSecret; saved.lastTotpStep = step
        } else {
            emailGuard.lock()
            if (!notifications.availableOtp("email")) throw BadRequestException("Restore email delivery before enabling email MFA")
            if (request.method != MfaMethod.EMAIL || !verifyEmail(challenge, request.code)) fail(challenge)
            saved.emailEnabled = true
        }
        val codes = if (first || recovery(saved).isEmpty()) newRecovery(saved) else emptyList()
        challenge.consumed = true; challenge.pendingSecret = null; challenge.emailCodeHash = null
        challenges.saveAndFlush(challenge); credentials.saveAndFlush(saved)
        user.sessionVersion++; users.saveAndFlush(user)
        return user to codes
    }

    @Transactional(noRollbackFor = [BadCredentialsException::class, TooManyRequestsException::class])
    fun disable(email: String, request: MfaProofRequest): User {
        val user = user(email, true); checkPassword(user, request.password)
        val saved = credentials.locked(user.id).orElse(null) ?: invalid()
        checkProof(saved, request.method, request.code, request.challengeToken)
        if (request.target in setOf("ALL", "TOTP")) { saved.totpSecret = null; saved.lastTotpStep = -1 }
        if (request.target in setOf("ALL", "EMAIL")) saved.emailEnabled = false
        if (!saved.enabled()) saved.recoveryHashes = "[]"
        credentials.saveAndFlush(saved); user.sessionVersion++; users.saveAndFlush(user)
        return user
    }

    @Transactional(noRollbackFor = [BadCredentialsException::class, TooManyRequestsException::class])
    fun regenerate(email: String, request: MfaProofRequest): Pair<User, List<String>> {
        val user = user(email, true); checkPassword(user, request.password)
        val saved = credentials.locked(user.id).orElse(null) ?: invalid()
        checkProof(saved, request.method, request.code, request.challengeToken)
        val codes = newRecovery(saved); credentials.saveAndFlush(saved)
        user.sessionVersion++; users.saveAndFlush(user)
        return user to codes
    }

    private fun checkProof(saved: MfaCredential, method: MfaMethod, code: String, raw: String) {
        rates.check("mfa-manage-account", saved.userId.toString(), 10, 900)
        val challenge = if (method == MfaMethod.EMAIL) challenge(raw, ChallengePurpose.MANAGE_EMAIL).also { if (it.userId != saved.userId) invalid() } else null
        if (!saved.enabled() || !verify(saved, method, code, challenge)) {
            if (challenge != null) fail(challenge) else invalid()
        }
        challenge?.let { it.consumed = true; challenges.saveAndFlush(it) }
    }
    private fun verify(saved: MfaCredential, method: MfaMethod, code: String, challenge: MfaChallenge?): Boolean = when (method) {
        MfaMethod.TOTP -> saved.totpSecret?.let { Totp.verify(it, code, saved.lastTotpStep, Instant.now().epochSecond) }
            ?.also { saved.lastTotpStep = it } != null
        MfaMethod.EMAIL -> saved.emailEnabled && challenge != null && verifyEmail(challenge, code)
        MfaMethod.RECOVERY -> {
            val hashes = recovery(saved).toMutableList(); val hash = SecureValues.hash("recovery:${saved.userId}:${code.uppercase().replace("-", "").replace(" ", "")}")
            val found = hashes.firstOrNull { SecureValues.equal(it, hash) }
            if (found != null) { hashes.remove(found); saved.recoveryHashes = mapper.writeValueAsString(hashes); true } else false
        }
    }
    private fun verifyEmail(challenge: MfaChallenge, code: String) = Regex("[0-9]{6}").matches(code) &&
        challenge.emailCodeHash?.let { SecureValues.equal(it, tokens.proof("${challenge.id}:$code")) } == true
    private fun sendCode(challenge: MfaChallenge) {
        if (!notifications.availableOtp("email")) throw BadRequestException("Configure working email delivery before using email MFA")
        if (challenge.emailSentAt?.isAfter(Instant.now().minusSeconds(60)) == true) throw TooManyRequestsException()
        rates.check("mfa-email-user", challenge.userId.toString(), 5, 900)
        val user = users.findById(challenge.userId).orElse(null)?.takeIf { it.active } ?: invalid()
        val code = tokens.code()
        val result = notifications.sendOtp("email",com.starter.notification.NotificationTarget(user.id,user.email,name=user.name ?: user.username,verifiedMethods=setOf("email")),code,5)
        if (result.status != "ACCEPTED") throw EmailDeliveryException("Unable to send a verification email. Use an authenticator or recovery code, or restore email delivery.")
        challenge.emailCodeHash = tokens.proof("${challenge.id}:$code"); challenge.emailSentAt = Instant.now()
        challenges.saveAndFlush(challenge)
    }
    private fun create(user: User, purpose: ChallengePurpose): Pair<String, MfaChallenge> {
        val raw = tokens.token()
        return raw to challenges.saveAndFlush(MfaChallenge(SecureValues.hash(raw), user.id, purpose, Instant.now().plusSeconds(300), user.sessionVersion))
    }
    private fun challenge(raw: String, purpose: ChallengePurpose): MfaChallenge {
        val value = challenges.locked(SecureValues.hash(raw)).orElse(null) ?: invalid()
        validateChallenge(value); if (value.purpose != purpose) invalid(); return value
    }
    private fun validateChallenge(value: MfaChallenge) {
        if (value.consumed || value.attempts >= 5 || value.expiresAt.isBefore(Instant.now())) invalid()
        val user = users.findById(value.userId).orElse(null)?.takeIf { it.active } ?: invalid()
        if (user.sessionVersion != value.sessionVersion) invalid()
    }
    private fun fail(value: MfaChallenge): Nothing { value.attempts++; challenges.saveAndFlush(value); invalid() }
    private fun checkPassword(user: User, password: String) {
        rates.check("mfa-password", user.id.toString(), 10, 900)
        if (!passwords.matches(password, user.password)) invalid()
    }
    private fun user(email: String, locked: Boolean = false) =
        (if (locked) users.lockedByEmail(email) else users.findByEmail(email)).orElse(null)?.takeIf { it.active } ?: invalid()
    private fun recovery(saved: MfaCredential): List<String> = mapper.readValue(saved.recoveryHashes, object : TypeReference<List<String>>() {})
    private fun newRecovery(saved: MfaCredential): List<String> {
        val codes = (1..10).map { tokens.token(16).let { SecureValues.hash(it).take(32).uppercase().chunked(8).joinToString("-") } }
        saved.recoveryHashes = mapper.writeValueAsString(codes.map { SecureValues.hash("recovery:${saved.userId}:${it.replace("-", "")}") })
        return codes
    }
    private fun invalid(): Nothing = throw MfaVerificationException()
    @Scheduled(fixedDelay = 3_600_000) @Transactional fun purge() { challenges.deleteByExpiresAtBefore(Instant.now().minusSeconds(3600)) }
}

class MfaVerificationException : BadCredentialsException("Verification failed. Check your password and use a fresh verification or recovery code.")
