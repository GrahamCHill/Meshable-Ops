package com.starter.model.postgres

import jakarta.persistence.*

@Entity @Table(name = "plugin_settings")
class PluginSettings(@Id val pluginId: String = "", var enabled: Boolean = false,
    @Column(nullable = false, length = 40) var allowedRolesCsv: String = "ADMIN,USER",
    @Column(nullable = false, columnDefinition = "boolean default false") var searchEnabled: Boolean = false,
    @Column(columnDefinition = "TEXT DEFAULT '{}'", nullable = false) var tableAccessJson: String = "{}",
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("false") var removed: Boolean = false)
