package com.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJpaRepositories(basePackages = ["com.starter.repository.postgres", "com.starter.mfa", "com.starter.webhook", "com.starter.share", "com.starter.plugins", "com.starter.notification", "com.starter.access", "com.starter.projects", "com.starter.security"])
@EnableScheduling
class StarterApplication

fun main(args: Array<String>) {
    runApplication<StarterApplication>(*args)
}
