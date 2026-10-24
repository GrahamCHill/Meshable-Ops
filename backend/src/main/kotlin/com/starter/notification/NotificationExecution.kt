package com.starter.notification

import org.springframework.context.annotation.*
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration @EnableAsync
class NotificationExecution {
    @Bean("notificationExecutor") fun executor()=ThreadPoolTaskExecutor().apply {corePoolSize=2;maxPoolSize=2;queueCapacity=100;setThreadNamePrefix("notification-");setRejectedExecutionHandler(java.util.concurrent.ThreadPoolExecutor.DiscardPolicy())}
}
