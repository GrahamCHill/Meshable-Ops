package com.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app")
class AdminSeedProperties {
    var seedAdmin = UserSeedProperties().apply {
        role = "ADMIN"
    }
    var seedUser = UserSeedProperties()
    var bootstrapUsersJson = ""
    var seedDashboard = DashboardSeedProperties()
}

class UserSeedProperties {
    // Development defaults and environment overrides come from application.properties.
    var email = ""
    var username = ""
    var password = ""
    var role = "USER"

    fun toSeed() = BootstrapUserSeed(email = email, username = username, password = password, role = role)
}

class DashboardSeedProperties {
    var label = "Metrics Overview"
    var description = "Items created over time"
    var id = "fdda8aae-c6fb-48fc-9322-5f797764b9dd"
    var guestTokenResourceId = ""
    var allowedRoles = "ADMIN,USER"
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
