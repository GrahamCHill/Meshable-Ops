package com.starter.mfa

import com.starter.dto.MessageResponse
import com.starter.security.RateLimits
import com.starter.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class MfaController(private val mfa: MfaService, private val auth: AuthService, private val rates: RateLimits) {
    @PostMapping("/api/auth/mfa/verify")
    fun verify(@Valid @RequestBody body: MfaVerifyRequest, request: HttpServletRequest, response: HttpServletResponse): com.starter.dto.AuthResponse {
        rates.check("mfa-verify-ip", request.remoteAddr, 30, 900)
        return auth.issueSession(mfa.verifyLogin(body), true, response)
    }
    @PostMapping("/api/auth/mfa/email")
    fun email(@Valid @RequestBody body: MfaEmailRequest, request: HttpServletRequest): MessageResponse {
        rates.check("mfa-email-ip", request.remoteAddr, 10, 900); mfa.emailCode(body.challengeToken)
        return MessageResponse("Verification email accepted. Check your inbox.")
    }
    @GetMapping("/api/account/mfa") fun status(@AuthenticationPrincipal user: UserDetails) = mfa.status(user.username)
    @PostMapping("/api/account/mfa/dismiss") fun dismiss(@AuthenticationPrincipal user: UserDetails): MessageResponse {
        mfa.dismiss(user.username); return MessageResponse("MFA setup reminder dismissed")
    }
    @PostMapping("/api/account/mfa/setup/{kind}")
    fun setup(@PathVariable kind: String, @AuthenticationPrincipal user: UserDetails, @Valid @RequestBody body: MfaSetupRequest) =
        mfa.begin(user.username, when(kind) { "totp" -> ChallengePurpose.SETUP_TOTP; "email" -> ChallengePurpose.SETUP_EMAIL; "manage-email" -> ChallengePurpose.MANAGE_EMAIL
            else -> throw com.starter.exception.BadRequestException("Choose an MFA method") }, body)
    @PostMapping("/api/account/mfa/confirm")
    fun confirm(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody body: MfaVerifyRequest, response: HttpServletResponse): MfaCompleteResponse {
        val (account, codes) = mfa.confirm(user.username, body)
        return MfaCompleteResponse(auth.issueSession(account, true, response), codes)
    }
    @PostMapping("/api/account/mfa/disable")
    fun disable(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody body: MfaProofRequest, response: HttpServletResponse): MfaCompleteResponse {
        val account = mfa.disable(user.username, body)
        return MfaCompleteResponse(auth.issueSession(account, mfa.enabled(account.id), response))
    }
    @PostMapping("/api/account/mfa/recovery-codes")
    fun recovery(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody body: MfaProofRequest, response: HttpServletResponse): MfaCompleteResponse {
        val (account, codes) = mfa.regenerate(user.username, body)
        return MfaCompleteResponse(auth.issueSession(account, true, response), codes)
    }
}
