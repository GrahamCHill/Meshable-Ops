package com.starter.plugins.shared

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.starter.plugins.*
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Entity @Table(name="workspace_shared_settings",indexes=[Index(columnList="schema_id")])
class SharedSettingsRecord(@Id val id:UUID=UUID.randomUUID(),val schemaId:String="",var name:String="",var enabled:Boolean=false,
    @get:JsonIgnore @Convert(converter=EncryptedSecretConverter::class) @Column(columnDefinition="TEXT") var dataJson:String?=null,
    @Version var revision:Long=0,@get:JsonIgnore @Column(columnDefinition="TEXT") var secretFieldsCsv:String="") {override fun toString()="SharedSettingsRecord(id=$id, schemaId=$schemaId)"}
interface SharedSettingsRepository:JpaRepository<SharedSettingsRecord,UUID> {
    fun findBySchemaIdOrderByNameAsc(schemaId:String):List<SharedSettingsRecord>
    fun countBySchemaId(schemaId:String):Long
}
data class SharedProfileInput(val name:String,val enabled:Boolean=false,@get:JsonProperty(access=JsonProperty.Access.WRITE_ONLY) val values:Map<String,String> = emptyMap(),val clearSecrets:Set<String> = emptySet(),val revision:Long= -1) {override fun toString()="SharedProfileInput(revision=$revision)"}
data class SharedProfileView(val id:UUID,val schemaId:String,val name:String,val enabled:Boolean,val values:Map<String,String>,val configuredSecrets:Set<String>,val missingRequired:List<String>,val revision:Long)
data class SharedSchemaView(val schema:PluginSharedSettingsSchema,val ownerPluginId:String?,val path:String,val encryptionConfigured:Boolean)
data class SharedBindingInput(val profileId:UUID?,val revision:Long)
data class SharedBindingView(val reference:PluginSharedSettingsReference,val selectedProfileId:UUID?,val profiles:List<SharedProfileView>,val revision:Long,val settingsPath:String?)

object IncomingEmailSchema {
    const val ID="communications.imaps"
    val definition=PluginSharedSettingsSchema(ID,"Incoming email (IMAPS)","Read incoming mail securely. SMTP or an email API sends messages; IMAPS reads replies and cannot send them.",
        listOf(PluginSettingField("host","IMAPS server",required=true,help="Your provider's incoming mail hostname. It can differ from the SMTP server.",maxLength=253),
            PluginSettingField("port","IMAPS port",help="IMAPS uses TLS from the start. The usual port is 993.",defaultValue="993"),
            PluginSettingField("username","IMAPS username",required=true,help="The incoming mailbox login, often its email address.",maxLength=320),
            PluginSettingField("password","IMAPS password or app password",PluginFieldType.SECRET,required=true,maxLength=4096,help="Use the provider's mailbox/app password. It is independent of the outbound SMTP credential."),
            PluginSettingField("folder","Mailbox folder",defaultValue="INBOX",maxLength=200,help="Use a dedicated mailbox or folder for automated reply handling.")),
        PluginAdminMenu("communications","Communications","Incoming email (IMAPS)","mail"),setOf("host","port","username"))
}
