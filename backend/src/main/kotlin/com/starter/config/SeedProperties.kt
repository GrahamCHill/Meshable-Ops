package com.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app")
class BootstrapProperties {
    var bootstrapUsersJson = ""
}

@ConfigurationProperties("app.seed-storage")
class StorageSeedProperties {
    var provider = "NONE"
    var bucket = ""
    var region = "us-east-1"
    var endpoint = ""
    var accessKey = ""
    var secretKey = ""
    var rootPath = "folders"
    var force = false
}
