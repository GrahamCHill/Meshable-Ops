package com.starter.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

class SaltedPasswordEncoder : PasswordEncoder {
    private val bcrypt = BCryptPasswordEncoder(12)
    override fun encode(rawPassword: CharSequence): String {
        require(rawPassword.toString().toByteArray(Charsets.UTF_8).size <= 72) { "Password must be at most 72 UTF-8 bytes" }
        return bcrypt.encode(rawPassword)
    }
    override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
        rawPassword.toString().toByteArray(Charsets.UTF_8).size <= 72 && bcrypt.matches(rawPassword, encodedPassword)
    override fun upgradeEncoding(encodedPassword: String) = bcrypt.upgradeEncoding(encodedPassword)
}
