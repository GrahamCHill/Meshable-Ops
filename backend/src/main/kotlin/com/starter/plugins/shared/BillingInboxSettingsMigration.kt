package com.starter.plugins.shared

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.PluginConfigurationRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Move legacy inbox credentials once; retain an explicitly selected shared connection. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
class BillingInboxSettingsMigration(private val configurations:PluginConfigurationRepository,private val profiles:SharedSettingsRepository,
    private val mapper:ObjectMapper,private val jdbc:JdbcTemplate):ApplicationRunner {
    @Transactional override fun run(args:ApplicationArguments) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"plugin-configuration:billing")
        val row=configurations.findById("billing").orElse(null) ?: return
        val values=row.dataJson?.let {mapper.readValue(it,object:TypeReference<MutableMap<String,String>>() {})} ?: return
        val mapping=mapOf("imapHost" to "host","imapPort" to "port","imapUsername" to "username","imapPassword" to "password","imapFolder" to "folder")
        if(mapping.keys.none {it in values})return
        val id=UUID.nameUUIDFromBytes("meshable:legacy:billing:incoming-email".toByteArray())
        if(!profiles.existsById(id)) {
            val incoming=mapOf("port" to "993","folder" to "INBOX")+mapping.mapNotNull {(old,new)->values[old]?.takeIf {it.isNotBlank()}?.let {new to it}}.toMap()
            val enabled=values["inboxEnabled"]=="true"&&listOf("host","username","password").all {!incoming[it].isNullOrBlank()}
            profiles.saveAndFlush(SharedSettingsRecord(id,IncomingEmailSchema.ID,"Imported invoice inbox",enabled,mapper.writeValueAsString(incoming),secretFieldsCsv="password"))
        }
        values.putIfAbsent(PluginSharedSettingsService.PREFIX+"incoming-email",id.toString())
        mapping.keys.forEach(values::remove)
        row.dataJson=mapper.writeValueAsString(values);configurations.saveAndFlush(row)
    }
}
