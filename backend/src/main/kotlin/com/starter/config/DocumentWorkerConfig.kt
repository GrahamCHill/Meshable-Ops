package com.starter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class DocumentWorkerConfig {
    @Bean("documentTaskScheduler")
    fun documentTaskScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("document-processing-")
        setWaitForTasksToCompleteOnShutdown(false)
    }
}
