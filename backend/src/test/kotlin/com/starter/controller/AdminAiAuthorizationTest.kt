package com.starter.controller

import com.starter.ai.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class AdminAiAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class TestConfig {
        @Bean open fun settings() = mock(AiSettingsService::class.java)
        @Bean open fun provider() = mock(AiProviderClient::class.java)
        @Bean open fun embeddings() = mock(EmbeddingService::class.java)
        @Bean open fun controller(settings: AiSettingsService, provider: AiProviderClient, embeddings: EmbeddingService) = AdminAiController(settings, provider, embeddings)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `ordinary users cannot read configuration or trigger AI connection tests`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@test.local", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
            val controller = context.getBean(AdminAiController::class.java)
            assertThatThrownBy { controller.settings() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.test("chat") }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(AiSettingsService::class.java), context.getBean(AiProviderClient::class.java))
        }
    }
}
