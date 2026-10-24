package com.starter.mfa

import com.starter.dto.AuthResponse
import com.starter.dto.LoginResponse
import com.starter.security.PasswordBytes
import jakarta.validation.constraints.*

enum class MfaMethod { TOTP, EMAIL, RECOVERY }
data class MfaChallengeResponse(val mfaRequired: Boolean = true, val challengeToken: String, val methods: List<MfaMethod>, val expiresAt: String) : LoginResponse {
    override fun toString() = "MfaChallengeResponse(mfaRequired=true)"
}
data class MfaVerifyRequest(@field:NotBlank @field:Size(max = 128) val challengeToken: String, val method: MfaMethod, @field:NotBlank @field:Size(max = 128) val code: String) {
    override fun toString() = "MfaVerifyRequest(method=$method)"
}
data class MfaEmailRequest(@field:NotBlank @field:Size(max = 128) val challengeToken: String) { override fun toString() = "MfaEmailRequest()" }
data class MfaSetupRequest(@field:NotBlank @field:PasswordBytes val password: String,
    val method: MfaMethod? = null, @field:Size(max = 128) val code: String = "", @field:Size(max = 128) val challengeToken: String = "") {
    override fun toString() = "MfaSetupRequest()"
}
data class MfaSetupResponse(val challengeToken: String, val expiresAt: String, val secret: String? = null, val otpauthUri: String? = null) {
    override fun toString() = "MfaSetupResponse()"
}
data class MfaProofRequest(
    @field:NotBlank @field:PasswordBytes val password: String,
    val method: MfaMethod,
    @field:NotBlank @field:Size(max = 128) val code: String,
    @field:Size(max = 128) val challengeToken: String = "",
    @field:Pattern(regexp = "ALL|TOTP|EMAIL") val target: String = "ALL",
) { override fun toString() = "MfaProofRequest(method=$method)" }
data class MfaStatus(val totpEnabled: Boolean, val emailEnabled: Boolean, val emailAvailable: Boolean, val encryptionReady: Boolean,
    val recoveryCodesRemaining: Int, val setupRecommended: Boolean)
data class MfaCompleteResponse(val auth: AuthResponse, val recoveryCodes: List<String> = emptyList()) {
    override fun toString() = "MfaCompleteResponse()"
}
