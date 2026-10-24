package com.starter.notification

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class NotificationRecipientKind { EMAIL, PHONE, FIXED, MEMBER }
enum class NotificationCategory { GENERAL, WORKFLOW, SECURITY, TRANSACTIONAL }
data class NotificationMethod(val id: String,val label: String,val pluginId: String?,val recipientKind: NotificationRecipientKind,
    val supportsOtp: Boolean=false,val supportsHtml: Boolean=false,val supportsAttachments: Boolean=false,
    val parameterized: Boolean=false,val maxTextLength: Int=4000)
data class NotificationMethodView(val method: NotificationMethod,val enabled: Boolean,val available: Boolean)
data class NotificationTemplate(@field:Size(max=200) val subject: String="{{subject}}",
    @field:Size(max=20000) val text: String="{{message}}",@field:Size(max=100000) val html: String="",
    @field:Size(max=20) val parameters: List<@Size(max=1000) String> = listOf("{{message}}"),
    @field:Size(max=200) val providerTemplate: String="")
data class NotificationRule(val enabled: Boolean=true,val allowedMethods: Set<String> = setOf("email"),val defaultMethods: Set<String> = setOf("email"),
    val allowMemberChoice: Boolean=true,@field:Valid val templates: Map<String,NotificationTemplate> = emptyMap())
data class NotificationSettingsRequest(@field:Min(-1) val revision: Long=-1,val methods: Map<String,Boolean> = emptyMap(),
    @field:Valid val rules: Map<String,NotificationRule> = emptyMap())
data class NotificationSettingsView(val revision: Long,val methods: List<NotificationMethodView>,val notifications: List<NotificationDefinitionView>)
data class NotificationDefinition(val id: String,val label: String,val description: String,val category: NotificationCategory,
    val variables: Set<String>,val subject: String,val text: String,val enabledByDefault: Boolean=true,val pluginId: String?=null, val privateToRecipient: Boolean = false)
data class NotificationDefinitionView(val definition: NotificationDefinition,val rule: NotificationRule,val eligibleMethods: Set<String>)
data class NotificationConfiguration(val methods: Map<String,Boolean> = emptyMap(),val rules: Map<String,NotificationRule> = emptyMap())
data class NotificationPreferenceRequest(@field:Min(-1) val revision: Long=-1,val choices: Map<String,Set<String>> = emptyMap())
data class NotificationPreferenceItem(val id: String,val label: String,val description: String,val enabled: Boolean,val editable: Boolean,
    val defaults: Set<String>,val selected: Set<String>,val usingDefaults: Boolean,val methods: List<NotificationMethodView>)
data class NotificationPreferencesView(val revision: Long,val notifications: List<NotificationPreferenceItem>,val hasPhone: Boolean)
data class NotificationPreviewRequest(val notificationId: String,val methodId: String,@field:Valid val template: NotificationTemplate)
data class NotificationPreview(val subject: String,val text: String,val html: String,val parameters: List<String>)
data class NotificationSendRequest(val methodId: String,@field:Size(max=254) val recipient: String="",
    @field:Size(max=200) val subject: String="",@field:NotBlank @field:Size(max=20000) val message: String="",val userId: UUID?=null) {
    override fun toString()="NotificationSendRequest(methodId=$methodId)"
}
/** Contact verification belongs to the caller's authentication/enrollment flow, never a browser-supplied flag. */
data class NotificationTarget(val userId: UUID?=null,val email: String="",val phone: String="",val name: String="",val verifiedMethods: Set<String> = emptySet()) {
    override fun toString()="NotificationTarget(userId=$userId)"
}
/** Reviewed plugins can declare extra notification types without editing the core catalogue. */
interface NotificationDefinitionProvider { val pluginId: String; fun definitions(): List<NotificationDefinition> }

@Entity @Table(name="notification_settings")
class NotificationSettingsRecord(@Id val id: Int=1,@get:JsonIgnore @Column(columnDefinition="TEXT") var configurationJson: String="{}",@Version var revision: Long=0)
interface NotificationSettingsRepository : JpaRepository<NotificationSettingsRecord,Int>
@Entity @Table(name="notification_preferences")
class NotificationPreferenceRecord(@Id val userId: UUID=UUID.randomUUID(),@get:JsonIgnore @Column(columnDefinition="TEXT") var choicesJson: String="{}",@Version var revision: Long=0)
interface NotificationPreferenceRepository : JpaRepository<NotificationPreferenceRecord,UUID>
@Entity @Table(name="notification_deliveries",indexes=[Index(columnList="created_at"),Index(columnList="target_user_id,created_at")])
class NotificationDelivery(@Id val id: UUID=UUID.randomUUID(),val notificationId: String="",val methodId: String="",val targetUserId: UUID?=null,
    val recipientHint: String="",var status: String="SENDING",val createdAt: Instant=Instant.now(),var finishedAt: Instant?=null,
    @get:JsonIgnore @Column(unique=true,nullable=false,length=64) val deduplicationHash: String="") {
    override fun toString()="NotificationDelivery(id=$id,status=$status)"
}
interface NotificationDeliveryRepository : JpaRepository<NotificationDelivery,UUID> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<NotificationDelivery>
}
