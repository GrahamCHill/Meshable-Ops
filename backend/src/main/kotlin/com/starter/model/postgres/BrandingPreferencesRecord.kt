package com.starter.model.postgres

import com.starter.dto.LoginBackgroundMode
import jakarta.persistence.*

@Entity @Table(name = "branding_preferences")
class BrandingPreferencesRecord(
    @Id val id: Int = 1,
    @Column(nullable = false, length = 80) var applicationName: String = "Meshable Ops",
    @Column(length = 7) var sidebarBackgroundColor: String? = null,
    @Column(length = 7) var sidebarTextColor: String? = null,
    @Column(length = 7) var linkColor: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var loginBackgroundMode: LoginBackgroundMode = LoginBackgroundMode.NONE,
    var loginBackgroundSlot: Int? = null,
    @Column(nullable = false) var slideshowSeconds: Int = 8,
    @Version var revision: Long = 0,
    @Column(columnDefinition = "TEXT", nullable = false) @org.hibernate.annotations.ColumnDefault("'{}'") var themesJson: String = "{}",
)
