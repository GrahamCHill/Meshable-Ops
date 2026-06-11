package com.starter.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date

@Service
class JwtService {

    @Value("\${jwt.secret}") private lateinit var secret: String
    @Value("\${jwt.expiry-ms}") private var expiryMs: Long = 86_400_000L

    private fun key() = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateToken(userDetails: UserDetails, extraClaims: Map<String, Any> = emptyMap()): String =
        Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.username)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiryMs))
            .signWith(key())
            .compact()

    fun extractUsername(token: String): String? = extractClaim(token, Claims::getSubject)

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token) ?: return false
        return username == userDetails.username && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String) = extractClaim(token, Claims::getExpiration).before(Date())

    private fun <T> extractClaim(token: String, resolver: (Claims) -> T): T =
        resolver(Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).payload)
}
