package com.starter.controller

import com.starter.share.*
import com.starter.webhook.*
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
import java.util.UUID

class SharingWebhookAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class Config {
        @Bean open fun shares() = mock(FileShareService::class.java)
        @Bean open fun settings() = mock(ShareSettingsService::class.java)
        @Bean open fun webhooks() = mock(WebhookService::class.java)
        @Bean open fun shareController(shares: FileShareService, settings: ShareSettingsService) = AdminShareController(shares, settings)
        @Bean open fun webhookController(webhooks: WebhookService) = AdminWebhookController(webhooks)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `regular accounts cannot configure send inspect or revoke admin integrations`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@example.test", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val shares = context.getBean(AdminShareController::class.java)
            val hooks = context.getBean(AdminWebhookController::class.java)
            assertThatThrownBy { shares.list() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { shares.settings() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { shares.settings(ShareSettingsRequest(false, "")) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { shares.revoke(UUID.randomUUID()) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { shares.create(org.springframework.security.core.userdetails.User("user@example.test", "", emptyList()), CreateShareRequest(UUID.randomUUID(), "contract.pdf", "r@example.test", "example-password")) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { hooks.list() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { hooks.create(WebhookRequest("Example", WebhookDirection.INCOMING)) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { hooks.update(UUID.randomUUID(), WebhookRequest("Example", WebhookDirection.INCOMING)) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { hooks.history(UUID.randomUUID()) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { hooks.send(UUID.randomUUID(), WebhookSendRequest(data = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().createObjectNode())) }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(FileShareService::class.java), context.getBean(ShareSettingsService::class.java), context.getBean(WebhookService::class.java))
        }
    }
}
