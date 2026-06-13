package com.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJpaRepositories(basePackages = ["com.starter.repository.postgres"])
@EnableMongoRepositories(basePackages = ["com.starter.repository.mongo"])
@EnableScheduling
class StarterApplication

fun main(args: Array<String>) {
    runApplication<StarterApplication>(*args)
}
