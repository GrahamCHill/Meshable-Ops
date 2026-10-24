package com.starter.model.postgres

import jakarta.persistence.*

@Entity @Table(name = "workspace_navigation_settings")
class NavigationSettings(@Id val id: Int = 1,
    @Column(nullable = false, columnDefinition = "TEXT") var orderJson: String = "[]",
    @Column(nullable = false, columnDefinition = "TEXT") var submenusJson: String = "{}",
    @Version var revision: Long = 0)
