package com.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJpaRepositories(basePackages = ["com.starter.repository.postgres", "com.starter.mfa", "com.starter.webhook", "com.starter.share", "com.starter.documents", "com.starter.workflow", "com.starter.plugins", "com.starter.notification", "com.starter.access"])
@EnableMongoRepositories(basePackages = ["com.starter.repository.mongo"])
@EnableScheduling
class StarterApplication

fun main(args: Array<String>) {
    runApplication<StarterApplication>(*args)
}
