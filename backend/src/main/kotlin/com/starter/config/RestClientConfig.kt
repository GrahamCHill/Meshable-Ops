package com.starter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestTemplate
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.net.HttpURLConnection

@Configuration
class RestClientConfig {
    @Bean @Primary
    fun restTemplate() = RestTemplate()

    @Bean("documentRestTemplate")
    fun documentRestTemplate() = RestTemplate(object : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    }.apply { setConnectTimeout(10000); setReadTimeout(910000) })

    @Bean("aiRestTemplate")
    fun aiRestTemplate(): RestTemplate {
        val factory = object : SimpleClientHttpRequestFactory() {
            override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                super.prepareConnection(connection, httpMethod)
                connection.instanceFollowRedirects = false
            }
        }.apply { setConnectTimeout(10000); setReadTimeout(45000) }
        return RestTemplate(factory)
    }
}
