package com.starter.controller

import com.starter.service.DocumentAccessService
import org.assertj.core.api.Assertions.assertThatThrownBy
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

class DocumentAccessAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class TestConfig {
        @Bean open fun service(): DocumentAccessService = mock(DocumentAccessService::class.java)
        @Bean open fun controller(service: DocumentAccessService) = AdminDocumentAccessController(service)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()

    @Test fun `regular users cannot manage document policy`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@test.local", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
            val controller = context.getBean(AdminDocumentAccessController::class.java)
            assertThatThrownBy { controller.settings() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.save(com.starter.dto.DocumentAccessRequest(false, com.starter.model.postgres.DownloadMode.DISABLED), org.springframework.security.core.userdetails.User("user@test.local", "", emptyList())) }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(DocumentAccessService::class.java))
        }
    }
}
