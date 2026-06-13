package com.starter.service

import com.starter.repository.postgres.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            .or { userRepository.findByUsername(username) }
            .orElseThrow { UsernameNotFoundException("User not found: $username") }

        return User(
            user.email,
            user.password,
            user.active,
            true,
            true,
            true,
            listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
        )
    }
}
