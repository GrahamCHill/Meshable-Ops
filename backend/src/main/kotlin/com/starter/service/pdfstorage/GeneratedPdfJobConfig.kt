package com.starter.service.pdfstorage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class GeneratedPdfJobConfig {
    @Bean
    fun generatedPdfTaskScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("generated-pdf-deletion-")
    }
}
