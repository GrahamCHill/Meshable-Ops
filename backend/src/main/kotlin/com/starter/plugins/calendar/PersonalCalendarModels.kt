package com.starter.plugins.calendar

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity @Table(name="plugin_calendar_connections",uniqueConstraints=[UniqueConstraint(columnNames=["owner_id"])])
class PersonalCalendarConnection(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(), val providerId: String = "",
    var enabled: Boolean = false, @Version var revision: Long = 0,
    @Column(nullable=false) @org.hibernate.annotations.ColumnDefault("0") var configurationRevision: Long = 0,
    @get:JsonIgnore @Convert(converter=EncryptedSecretConverter::class) @Column(columnDefinition="TEXT") var credentialsJson: String = "{}",
    var nextSyncAt: Instant = Instant.now(), var lastSyncAt: Instant? = null,
    @Column(length=500) var status: String = "Not synced")

interface PersonalCalendarRepository : JpaRepository<PersonalCalendarConnection,UUID> {
    fun findByOwnerId(id: UUID): PersonalCalendarConnection?
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT c FROM PersonalCalendarConnection c WHERE c.id = :id")
    fun locked(id: UUID): java.util.Optional<PersonalCalendarConnection>
    @Query("SELECT c.id FROM PersonalCalendarConnection c WHERE c.enabled = true AND c.nextSyncAt <= :now ORDER BY c.nextSyncAt")
    fun due(now: Instant,pageable: Pageable): List<UUID>
}

@Entity @Table(name="plugin_calendar_sync_links",uniqueConstraints=[UniqueConstraint(columnNames=["connection_id","event_id"]),UniqueConstraint(columnNames=["connection_id","remote_id"])])
class CalendarSyncLink(@Id val id: UUID = UUID.randomUUID(),
    @get:JsonIgnore @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="connection_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) val connection: PersonalCalendarConnection = PersonalCalendarConnection(),
    @get:JsonIgnore @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="event_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) val event: CalendarEvent = CalendarEvent(),
    @Column(length=1000,nullable=false) val remoteId: String = "", @Column(length=1024) var remoteVersion: String = "",
    var localRevision: Long = 0, var remoteDeleted: Boolean = false)
interface CalendarSyncLinkRepository : JpaRepository<CalendarSyncLink,UUID> {
    fun findByConnection_Id(id: UUID): List<CalendarSyncLink>
    fun deleteByConnection_Id(id: UUID)
}

data class PersonalCalendarRequest(val providerId: String,val values: Map<String,String> = emptyMap(),val clearSecrets: Set<String> = emptySet(),
    val enabled: Boolean = false,val revision: Long = -1) {
    override fun toString() = "PersonalCalendarRequest(providerId=$providerId, enabled=$enabled)"
}
data class PersonalCalendarView(val providerId: String,val enabled: Boolean,val revision: Long,val values: Map<String,String>,val configuredSecrets: Set<String>,
    val missingRequired: List<String>,val lastSyncAt: Instant?,val status: String)
data class PersonalCalendarSettings(val connection: PersonalCalendarView?,val providers: List<PersonalCalendarSchema>,val canWrite: Boolean)
data class PersonalCalendarSchema(val id: String,val name: String,val fields: List<com.starter.plugins.PluginSettingField>)
