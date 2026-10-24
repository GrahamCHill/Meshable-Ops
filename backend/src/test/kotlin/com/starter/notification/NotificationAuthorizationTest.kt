package com.starter.notification

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

class NotificationAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass=true)
    open class Config {
        @Bean open fun settings()=mock(NotificationSettingsService::class.java)
        @Bean open fun catalogue()=mock(NotificationCatalogue::class.java)
        @Bean open fun transports()=mock(NotificationTransports::class.java)
        @Bean open fun renderer()=mock(NotificationRenderer::class.java)
        @Bean open fun deliveries()=mock(NotificationDeliveryRepository::class.java)
        @Bean open fun notifications()=mock(NotificationService::class.java)
        @Bean open fun publisher()=mock(NotificationPublisher::class.java)
        @Bean open fun actors()=mock(com.starter.plugins.PluginActor::class.java)
        @Bean open fun rates()=mock(com.starter.security.RateLimits::class.java)
        @Bean open fun controller(settings: NotificationSettingsService,catalogue: NotificationCatalogue,transports: NotificationTransports,renderer: NotificationRenderer,deliveries: NotificationDeliveryRepository,notifications: NotificationService,publisher: NotificationPublisher,actors: com.starter.plugins.PluginActor,rates: com.starter.security.RateLimits)=AdminNotificationController(settings,catalogue,transports,renderer,deliveries,notifications,publisher,actors,rates)
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    @Test fun `members cannot change notification rules preview admin templates view delivery metadata or use the sender`() {
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken("member@example.test","",listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(Config::class.java).use {context ->
            val controller=context.getBean(AdminNotificationController::class.java)
            val user=org.springframework.security.core.userdetails.User("member@example.test","",emptyList())
            assertThatThrownBy {controller.settings(user)}.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy {controller.save(user,NotificationSettingsRequest())}.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy {controller.preview(user,NotificationPreviewRequest("general.message","email",NotificationTemplate()))}.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy {controller.history(user)}.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy {controller.send(user,NotificationSendRequest("email",message="Body"))}.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(NotificationService::class.java),context.getBean(NotificationSettingsService::class.java),context.getBean(NotificationDeliveryRepository::class.java))
        }
    }
}
