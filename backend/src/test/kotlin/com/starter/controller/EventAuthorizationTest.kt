package com.starter.controller

import com.starter.repository.mongo.EventRepository
import com.starter.service.EventHistoryPage
import com.starter.service.EventHistoryService
import com.starter.exception.BadRequestException
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

class EventAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass=true)
    open class Config {
        @Bean open fun events():EventRepository=mock(EventRepository::class.java)
        @Bean open fun history():EventHistoryService=mock(EventHistoryService::class.java)
        @Bean open fun controller(events:EventRepository,history:EventHistoryService)=EventController(events,history)
    }
    private fun signIn(role:String) {SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken("test@example.test","",listOf(SimpleGrantedAuthority("ROLE_$role")))}
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    @Test fun `both event APIs deny ordinary and external members before reading data`() {
        AnnotationConfigApplicationContext(Config::class.java).use {context->
            val controller=context.getBean(EventController::class.java)
            listOf("USER","EXTERNAL").forEach {role->signIn(role)
                assertThatThrownBy {controller.history(null,50)}.isInstanceOf(AccessDeniedException::class.java)
                assertThatThrownBy {controller.list(0,20)}.isInstanceOf(AccessDeniedException::class.java)
            }
            verifyNoInteractions(context.getBean(EventRepository::class.java),context.getBean(EventHistoryService::class.java))
        }
    }
    @Test fun `administrators can read history and legacy pagination remains bounded`() {
        signIn("ADMIN")
        AnnotationConfigApplicationContext(Config::class.java).use {context->
            val history=context.getBean(EventHistoryService::class.java)
            `when`(history.history(null,50)).thenReturn(EventHistoryPage(emptyList(),null))
            val controller=context.getBean(EventController::class.java)
            assertThat(controller.history(null,50).content).isEmpty()
            listOf(-1 to 20,0 to 0,0 to 101).forEach {(page,size)->assertThatThrownBy {controller.list(page,size)}.isInstanceOf(BadRequestException::class.java)}
            verifyNoInteractions(context.getBean(EventRepository::class.java))
        }
    }
}
