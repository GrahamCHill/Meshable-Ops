package com.starter.config

import com.starter.service.JwtService
import com.starter.service.UserDetailsServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.context.annotation.*
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.web.MockServletContext
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

class CrawlerHeadersTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig::class)
    open class Config {
        @Bean open fun jwtService() = mock(JwtService::class.java)
        @Bean open fun users() = mock(UserDetailsServiceImpl::class.java)
        @Bean open fun jwtFilter(jwt: JwtService, users: UserDetailsServiceImpl) = JwtAuthFilter(jwt, users,mock(com.starter.security.BrowserSessions::class.java))
        @Bean open fun controller() = PublicController()
    }
    @RestController
    class PublicController {
        @GetMapping("/api/health") fun health() = mapOf("status" to "UP")
        @GetMapping("/api/shared/test/pdf", produces = ["application/pdf"])
        fun pdf() = byteArrayOf(1, 2, 3)
    }

    @Test fun `crawler headers cover public content PDF responses denied requests and missing routes`() {
        AnnotationConfigWebApplicationContext().use { context ->
            context.servletContext = MockServletContext()
            context.environment.propertySources.addFirst(MapPropertySource("test", mapOf(
                "cors.allowed-origins" to "http://localhost:3000",
                "jwt.expiry-ms" to "3600000",
                "jwt.secret" to "crawler-policy-test-key-32-bytes-minimum",
            )))
            context.register(Config::class.java); context.refresh()
            val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val cases = listOf(
                get("/api/health") to 200,
                get("/api/shared/test/pdf") to 200,
                get("/api/private") to 403,
                get("/missing").with(user("test")) to 404,
            )
            cases.forEach { (request, expected) ->
                val response = mvc.perform(request).andReturn().response
                assertThat(response.status).isEqualTo(expected)
                assertThat(response.getHeader("X-Robots-Tag")!!.split(",").map { it.trim() })
                    .contains("noindex", "nofollow", "nosnippet", "noarchive", "noimageindex")
            }
        }
    }
}
