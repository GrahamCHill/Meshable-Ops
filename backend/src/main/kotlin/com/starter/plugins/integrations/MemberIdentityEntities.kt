package com.starter.plugins.integrations

import jakarta.persistence.*
import java.util.UUID

@MappedSuperclass
abstract class MemberIdentityEntity(@Id val userId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 200, unique = true) var remoteUserId: String = "",
    @Column(nullable = false, length = 160) var remoteName: String = "",
    @Column(nullable = false, length = 200) var destinationId: String = "",
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0") var revision: Long = 0)

@Entity @Table(name = "plugin_discord_identities") class DiscordMemberIdentity : MemberIdentityEntity()
@Entity @Table(name = "plugin_telegram_identities") class TelegramMemberIdentity : MemberIdentityEntity()
@Entity @Table(name = "plugin_teams_identities") class TeamsMemberIdentity : MemberIdentityEntity()
