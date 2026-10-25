package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.exception.*
import com.starter.plugins.operations.BillingPlugin
import com.starter.plugins.shared.*
import com.starter.security.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import java.util.UUID

class SharedSettingsTest {
    private val json=jacksonObjectMapper()
    private val rows=mock(SharedSettingsRepository::class.java)
    private val registry=mock(PluginRegistry::class.java)
    private val access=mock(AccessControlService::class.java)
    private val encryption=testEncryption()
    private val profiles=mutableMapOf<UUID,SharedSettingsRecord>()
    private val service=SharedSettingsService(rows,registry,json,encryption,access)
    private val reference=PluginSharedSettingsReference("incoming-email",IncomingEmailSchema.ID,"Inbox",setOf("host","port","username","password","folder"))
    private val values=mapOf("host" to "imap.example.test","username" to "billing@example.test","password" to "private-shared-canary")
    init {
        `when`(registry.installed()).thenReturn(emptyList())
        `when`(access.allows(anyString())).thenReturn(true)
        `when`(rows.findById(any(UUID::class.java))).thenAnswer {Optional.ofNullable(profiles[it.getArgument(0)])}
        `when`(rows.existsById(any(UUID::class.java))).thenAnswer {profiles.containsKey(it.getArgument(0))}
        `when`(rows.findBySchemaIdOrderByNameAsc(anyString())).thenAnswer {call->profiles.values.filter {it.schemaId==call.getArgument<String>(0)}}
        `when`(rows.saveAndFlush(any(SharedSettingsRecord::class.java))).thenAnswer {call->call.getArgument<SharedSettingsRecord>(0).also {it.revision++;profiles[it.id]=it}}
    }
    private fun create(enabled:Boolean=true)=service.save(IncomingEmailSchema.ID,null,SharedProfileInput("Billing inbox",enabled,values))
    @Test fun `shared credentials are encrypted write only and never exposed by public runtime`() {
        val saved=create()
        assertThat(saved.values).containsEntry("host","imap.example.test").doesNotContainKey("password")
        assertThat(saved.configuredSecrets).containsExactly("password")
        assertThat(service.runtime(saved.id,reference)).containsEntry("password","private-shared-canary")
        assertThat(service.publicRuntime(saved.id,reference)).doesNotContainKey("password")
        assertThat(json.writeValueAsString(saved)).doesNotContain("private-shared-canary")
        assertThat(json.writeValueAsString(profiles.getValue(saved.id))).doesNotContain("private-shared-canary","dataJson","secretFieldsCsv")
        val converter=EncryptedSecretConverter(encryption)
        val stored=converter.convertToDatabaseColumn(profiles.getValue(saved.id).dataJson)
        assertThat(stored).startsWith("enc:v1:").doesNotContain("private-shared-canary","imap.example.test")
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(profiles.getValue(saved.id).dataJson)
        assertThat(SharedProfileInput("Inbox",values=values).toString()).doesNotContain("private-shared-canary")
    }
    @Test fun `blank password preserves it but changing identity requires replacement`() {
        val saved=create()
        val kept=service.save(saved.schemaId,saved.id,SharedProfileInput(saved.name,true,mapOf("password" to ""),revision=saved.revision))
        assertThat(service.runtime(saved.id,reference)).containsEntry("password","private-shared-canary")
        assertThatThrownBy {service.save(saved.schemaId,saved.id,SharedProfileInput(saved.name,true,mapOf("host" to "another.example.test"),revision=kept.revision))}.isInstanceOf(BadRequestException::class.java)
        val changed=service.save(saved.schemaId,saved.id,SharedProfileInput(saved.name,false,mapOf("host" to "another.example.test"),revision=kept.revision))
        assertThat(changed.configuredSecrets).isEmpty()
        assertThat(profiles.getValue(saved.id).dataJson).doesNotContain("private-shared-canary")
        assertThatThrownBy {service.runtime(saved.id,reference)}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `incomplete drafts unknown fields stale writes and wrong schemas fail safely`() {
        val draft=service.save(IncomingEmailSchema.ID,null,SharedProfileInput("Draft"))
        assertThat(draft.enabled).isFalse()
        assertThatThrownBy {service.save(draft.schemaId,draft.id,SharedProfileInput("Draft",true,revision=draft.revision))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {service.save(draft.schemaId,draft.id,SharedProfileInput("Draft",values=mapOf("unlisted" to "x"),revision=draft.revision))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {service.save(draft.schemaId,draft.id,SharedProfileInput("Draft",revision=-1))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.runtime(draft.id,reference.copy(schemaId="foreign.schema"))}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {service.httpCredential(draft.id,reference,"password","https://example.com")}.isInstanceOf(ForbiddenException::class.java)
        doThrow(ForbiddenException("denied")).`when`(access).requireFeature("admin.notifications")
        assertThatThrownBy {service.profiles(IncomingEmailSchema.ID)}.isInstanceOf(ForbiddenException::class.java)
    }
    private fun provider():PluginSharedSettingsSchema {
        val schema=PluginSharedSettingsSchema("plugin.provider.account","Provider account",fields=listOf(
            PluginSettingField("origin","HTTPS origin",required=true),PluginSettingField("token","API token",PluginFieldType.SECRET,required=true)),
            adminMenu=PluginAdminMenu("communications","Communications"),identityFields=setOf("origin"),httpOriginField="origin")
        val m=PluginManifest("provider","Provider","1.0.0","Shared provider",extensions=setOf(PluginExtension.SETTINGS),sharedSettingsSchemas=listOf(schema))
        `when`(registry.installed()).thenReturn(listOf(PluginView(m,true,setOf("ADMIN"))))
        return schema
    }
    @Test fun `plugin schemas use field allowlists owner activation and origin bound host credentials`() {
        val schema=provider()
        val saved=service.save(schema.id,null,SharedProfileInput("Provider",true,mapOf("origin" to "https://example.com","token" to "private-provider-canary")))
        val ref=PluginSharedSettingsReference("account",schema.id,"Account",setOf("origin","token"))
        assertThat(service.publicRuntime(saved.id,ref)).containsExactlyEntriesOf(mapOf("origin" to "https://example.com"))
        assertThat(service.runtime(saved.id,ref.copy(fields=setOf("origin")))).doesNotContainKey("token")
        assertThatThrownBy {service.runtime(saved.id,ref.copy(fields=setOf("foreign")))}.isInstanceOf(BadRequestException::class.java)
        val configurations=mock(PluginConfigurationRepository::class.java)
        val row=PluginConfigurationRecord("consumer",json.writeValueAsString(mapOf("@shared:account" to saved.id.toString())))
        `when`(configurations.findById("consumer")).thenReturn(Optional.of(row))
        `when`(registry.definition("consumer")).thenReturn(PluginManifest("consumer","Consumer","1.0.0","Consumer",sharedSettings=listOf(ref)))
        val bindings=PluginSharedSettingsService(registry,configurations,service,json,mock(JdbcTemplate::class.java))
        assertThat(bindings.httpCredential("consumer","account","token","https://example.com")).isEqualTo("private-provider-canary")
        clearInvocations(rows)
        assertThat(bindings.httpCredential("consumer","account","token","https://example.com")).isEqualTo("private-provider-canary")
        verify(rows,times(1)).findById(saved.id) // Origin and secret use the same snapshot.
        `when`(configurations.findById("consumer-two")).thenReturn(Optional.of(PluginConfigurationRecord("consumer-two",row.dataJson)))
        val secondConsumer=registry.definition("consumer").copy(id="consumer-two")
        `when`(registry.definition("consumer-two")).thenReturn(secondConsumer)
        service.save(schema.id,saved.id,SharedProfileInput(saved.name,true,mapOf("token" to "private-replacement-canary"),revision=saved.revision))
        listOf("consumer","consumer-two").forEach {id->assertThat(bindings.httpCredential(id,"account","token","https://example.com")).isEqualTo("private-replacement-canary")}
        assertThatThrownBy {bindings.httpCredential("consumer","account","token","https://evil.example")}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {bindings.current("consumer","undeclared")}.isInstanceOf(BadRequestException::class.java)
        doThrow(NotFoundException("disabled provider")).`when`(registry).requireEnabled("provider","ADMIN")
        assertThatThrownBy {bindings.current("consumer","account")}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `provider origin cannot contain paths or query and old secret remains private after schema changes`() {
        val schema=provider()
        assertThatThrownBy {service.save(schema.id,null,SharedProfileInput("Provider",true,mapOf("origin" to "https://example.com/path","token" to "secret")))}.isInstanceOf(BadRequestException::class.java)
        val saved=service.save(schema.id,null,SharedProfileInput("Provider",true,mapOf("origin" to "https://example.com","token" to "private-provider-canary")))
        val changed=schema.copy(fields=schema.fields.map {if(it.key=="token")it.copy(type=PluginFieldType.TEXT)else it})
        `when`(registry.installed()).thenReturn(listOf(PluginView(PluginManifest("provider","Provider","1.1.0","Changed schema",sharedSettingsSchemas=listOf(changed)),true,setOf("ADMIN"))))
        assertThat(service.profiles(schema.id).single().values).doesNotContainKey("token")
        assertThat(service.publicRuntime(saved.id,PluginSharedSettingsReference("account",schema.id,"Account",setOf("origin","token")))).doesNotContainKey("token")
        profiles.getValue(saved.id).dataJson=json.writeValueAsString(mapOf("origin" to "https://example.com","retiredField" to "legacy-private-value"))
        assertThat(service.profiles(schema.id).single().values).doesNotContainKey("retiredField")
    }
    @Test fun `bindings are explicit revision checked and local configuration saves retain them`() {
        val saved=create(false)
        val configurations=mock(PluginConfigurationRepository::class.java)
        val row=PluginConfigurationRecord("billing",json.writeValueAsString(mapOf("defaultCurrency" to "USD")))
        `when`(configurations.findById("billing")).thenReturn(Optional.of(row))
        `when`(configurations.saveAndFlush(row)).thenAnswer {row.revision++;row}
        `when`(registry.definition("billing")).thenReturn(BillingPlugin().manifest)
        `when`(registry.missingConfiguration(eq("billing") ?: "billing",anyMap())).thenReturn(emptyList())
        val bindings=PluginSharedSettingsService(registry,configurations,service,json,mock(JdbcTemplate::class.java))
        val view=bindings.save("billing","incoming-email",SharedBindingInput(saved.id,row.revision)).single()
        assertThat(view.selectedProfileId).isEqualTo(saved.id)
        assertThatThrownBy {bindings.save("billing","incoming-email",SharedBindingInput(null,-1))}.isInstanceOf(ConflictException::class.java)
        PluginConfigurationService(registry,configurations,json,encryption).save("billing",PluginConfigurationRequest(mapOf("defaultCurrency" to "EUR"),revision=row.revision))
        assertThat(json.readTree(row.dataJson).path("@shared:incoming-email").asText()).isEqualTo(saved.id.toString())
        assertThat(json.readTree(row.dataJson).path("defaultCurrency").asText()).isEqualTo("EUR")
    }
    @Test fun `legacy billing inbox moves once without losing business settings or explicit bindings`() {
        val configurations=mock(PluginConfigurationRepository::class.java)
        val selected=UUID.randomUUID()
        val row=PluginConfigurationRecord("billing",json.writeValueAsString(mapOf("defaultCurrency" to "EUR","inboxEnabled" to "true","imapHost" to values.getValue("host"),"imapUsername" to values.getValue("username"),"imapPassword" to values.getValue("password"),"@shared:incoming-email" to selected.toString())))
        `when`(configurations.findById("billing")).thenReturn(Optional.of(row))
        val migration=BillingInboxSettingsMigration(configurations,rows,json,mock(JdbcTemplate::class.java))
        migration.run(DefaultApplicationArguments());migration.run(DefaultApplicationArguments())
        val migrated=profiles.values.single()
        assertThat(migrated.enabled).isTrue()
        assertThat(json.readTree(migrated.dataJson).path("password").asText()).isEqualTo("private-shared-canary")
        assertThat(row.dataJson).doesNotContain("imapHost","imapPassword","private-shared-canary").contains("EUR",selected.toString())
        verify(rows,times(1)).saveAndFlush(any(SharedSettingsRecord::class.java))
        verify(configurations,times(1)).saveAndFlush(row)
    }
    @Test fun `split settings and provided schema declarations are bounded namespaced and unambiguous`() {
        val m=BillingPlugin().manifest
        PluginManifests.validate(m)
        assertThat(m.settingsFields.map {it.key}).doesNotContain("imapHost","imapUsername","imapPassword")
        assertThat(m.settingsSections.single().category).isEqualTo("communications")
        assertThatThrownBy {PluginManifests.validate(m.copy(settingsSections=m.settingsSections+PluginSettingsSection("other","Other","workspace",setOf("inboxEnabled"))))}.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {PluginManifests.validate(m.copy(settingsSections=m.settingsSections.map {it.copy(fields=setOf("missing"))}))}.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {PluginManifests.validate(m.copy(sharedSettings=m.sharedSettings.map {it.copy(sectionId="missing")}))}.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {PluginManifests.validate(m.copy(sharedSettingsSchemas=listOf(provider())))}.isInstanceOf(IllegalArgumentException::class.java)
    }
    @Test fun `profile defaults are persisted so upgrades cannot silently change a destination`() {
        val schema=provider()
        val withDefault=schema.copy(fields=schema.fields.map {if(it.key=="origin")it.copy(defaultValue="https://example.com")else it})
        val manifest=PluginManifest("provider","Provider","1.0.0","Provider",sharedSettingsSchemas=listOf(withDefault))
        `when`(registry.installed()).thenReturn(listOf(PluginView(manifest,true,setOf("ADMIN"))))
        val saved=service.save(schema.id,null,SharedProfileInput("Provider",true,mapOf("token" to "private-provider-canary")))
        assertThat(json.readTree(profiles.getValue(saved.id).dataJson).path("origin").asText()).isEqualTo("https://example.com")
        val changed=withDefault.copy(fields=withDefault.fields.map {if(it.key=="origin")it.copy(defaultValue="https://other.example.com")else it})
        `when`(registry.installed()).thenReturn(listOf(PluginView(manifest.copy(sharedSettingsSchemas=listOf(changed)),true,setOf("ADMIN"))))
        val ref=PluginSharedSettingsReference("account",schema.id,"Account",setOf("origin","token"))
        assertThat(service.httpCredential(saved.id,ref,"token","https://example.com")).isEqualTo("private-provider-canary")
        assertThatThrownBy {service.httpCredential(saved.id,ref,"token","https://other.example.com")}.isInstanceOf(ForbiddenException::class.java)
    }
}
