package com.starter.controller

import com.starter.service.StorageConnectionService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class StorageConnectionAuthorizationTest {
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    open class TestConfig {
        @Bean open fun service(): StorageConnectionService = mock(StorageConnectionService::class.java)
        @Bean open fun controller(service: StorageConnectionService) = AdminStorageConnectionsController(service)
    }
    @AfterEach fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `regular users cannot manage storage connections`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@test.local", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
            assertThatThrownBy { context.getBean(AdminStorageConnectionsController::class.java).list() }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(StorageConnectionService::class.java))
        }
    }

    @Test
    fun `the existing ADMIN role can manage connections as super admin`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("admin@test.local", "", listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
            context.getBean(AdminStorageConnectionsController::class.java).list()
            verify(context.getBean(StorageConnectionService::class.java)).listAdmin()
        }
    }
}
