package com.starter.plugins.shared

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PluginSharedSettingsService(private val registry:PluginRegistry,private val records:PluginConfigurationRepository,
    private val shared:SharedSettingsService,private val mapper:ObjectMapper,private val jdbc:JdbcTemplate) {
    companion object {const val PREFIX="@shared:"}
    private fun values(id:String):Map<String,String> = records.findById(id).orElse(null)?.dataJson?.let {mapper.readValue(it,object:TypeReference<Map<String,String>>() {})}.orEmpty()
    private fun reference(id:String,ref:String)=registry.definition(id).sharedSettings.find {it.id==ref} ?: throw BadRequestException("Declare this shared settings reference in the plugin manifest")
    fun views(id:String):List<SharedBindingView> {
        val definition=registry.definition(id);val row=records.findById(id).orElse(null);val bindings=values(id)
        return definition.sharedSettings.map {ref->
            val schema=runCatching {shared.schema(ref.schemaId)}.getOrNull()
            SharedBindingView(ref,bindings[PREFIX+ref.id]?.let {runCatching {UUID.fromString(it)}.getOrNull()},
                if(schema==null)emptyList() else shared.profiles(ref.schemaId),row?.revision ?: -1,schema?.path)
        }
    }
    @Transactional fun save(id:String,refId:String,input:SharedBindingInput):List<SharedBindingView> {
        val ref=reference(id,refId);if(!registry.definition(id).hasAdminSettings)throw ForbiddenException("This plugin has no administrator settings")
        val schema=shared.schema(ref.schemaId).schema
        if(!schema.fields.map {it.key}.containsAll(ref.fields))throw BadRequestException("Use only declared fields from the shared schema")
        input.profileId?.let {if(shared.profile(it).schemaId!=ref.schemaId)throw BadRequestException("Select a connection matching the declared schema")}
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"plugin-configuration:$id")
        val old=records.findById(id).orElse(null)
        if((old?.revision ?: -1)!=input.revision)throw ConflictException("Plugin settings changed. Reload before selecting a connection")
        val values=values(id).toMutableMap();if(input.profileId==null)values.remove(PREFIX+refId)else values[PREFIX+refId]=input.profileId.toString()
        val row=old ?: PluginConfigurationRecord(id);row.dataJson=mapper.writeValueAsString(values);records.saveAndFlush(row)
        return views(id)
    }
    private fun selected(id:String,ref:String):Pair<UUID,PluginSharedSettingsReference> {
        // Callers also retain their normal plugin/record permission checks.
        registry.requireEnabled(id,"ADMIN")
        val declared=reference(id,ref)
        val profile=values(id)[PREFIX+ref]?.let {runCatching {UUID.fromString(it)}.getOrNull()} ?: throw BadRequestException("Select a shared connection in the plugin's Communications or settings section")
        return profile to declared
    }
    fun current(id:String,ref:String):Map<String,String> = selected(id,ref).let {(profile,declared)->shared.runtime(profile,declared)}
    fun publicCurrent(id:String,ref:String):Map<String,String> = selected(id,ref).let {(profile,declared)->shared.publicRuntime(profile,declared)}
    fun httpCredential(id:String,ref:String,key:String,origin:String):String {
        val (profile,declared)=selected(id,ref)
        return shared.httpCredential(profile,declared,key,origin)
    }
}
