package com.starter.plugins.shared

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.AccessControlService
import com.starter.exception.*
import com.starter.plugins.*
import com.starter.security.SecretEncryption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SharedSettingsService(private val rows:SharedSettingsRepository,private val registry:PluginRegistry,private val mapper:ObjectMapper,
    private val encryption:SecretEncryption,private val access:AccessControlService) {
    fun schemas():List<SharedSchemaView> = definitions().filter {(_,owner)->owner==null||registry.available("ADMIN").any {it.manifest.id==owner}}
        .filter {(schema,owner)->access.allows(feature(schema.id,owner))}.map {(schema,owner)->viewSchema(schema,owner)}
    private fun definitions()=listOf(IncomingEmailSchema.definition to null)+registry.installed().flatMap {p->p.manifest.sharedSettingsSchemas.map {it to p.manifest.id}}
    fun schema(id:String,authorize:Boolean=true):SharedSchemaView {
        val (schema,owner)=definitions().find {it.first.id==id} ?: throw NotFoundException("Shared settings schema unavailable")
        owner?.let {if(authorize)registry.requireAvailable(it,"ADMIN")else registry.requireEnabled(it,"ADMIN")}
        if(authorize)access.requireFeature(feature(id,owner))
        return viewSchema(schema,owner)
    }
    private fun feature(id:String,owner:String?)=if(owner==null&&id==IncomingEmailSchema.ID)"admin.notifications" else "admin.plugins"
    private fun viewSchema(schema:PluginSharedSettingsSchema,owner:String?)=SharedSchemaView(schema,owner,
        if(schema.id==IncomingEmailSchema.ID)"/admin/email/inbox" else "/admin/sections/${schema.adminMenu.category}/shared-settings/${schema.id}",encryption.configured)
    fun profiles(schemaId:String):List<SharedProfileView> {val schema=schema(schemaId).schema;return rows.findBySchemaIdOrderByNameAsc(schemaId).map {view(it,schema)}}
    fun profile(id:UUID):SharedProfileView {val row=row(id);return view(row,schema(row.schemaId).schema)}
    private fun row(id:UUID)=rows.findById(id).orElse(null) ?: throw NotFoundException("Shared connection unavailable")
    @Transactional fun save(schemaId:String,id:UUID?,input:SharedProfileInput):SharedProfileView {
        val schema=schema(schemaId).schema;encryption.requireConfigured()
        val fields=schema.fields.associateBy {it.key}
        if(input.name.isBlank()||input.name.length>100||input.name.any(Char::isISOControl))throw BadRequestException("Use a connection name of 1–100 characters")
        if(input.values.keys.any {it !in fields}||input.clearSecrets.any {fields[it]?.type!=PluginFieldType.SECRET})throw BadRequestException("Use declared shared connection fields")
        val row=id?.let(::row) ?: SharedSettingsRecord(schemaId=schemaId)
        if(row.schemaId!=schemaId)throw NotFoundException("Shared connection unavailable")
        if((if(id==null)-1 else row.revision)!=input.revision)throw ConflictException("Shared connection changed. Reload before editing")
        if(id==null&&rows.countBySchemaId(schemaId)>=100)throw BadRequestException("Use at most 100 connections per schema")
        // Snapshot defaults too: an upgrade must not change a connection's server/account implicitly.
        val previous=decode(row);val values=effective(schema,previous).toMutableMap()
        input.values.forEach {(key,raw)->val f=fields.getValue(key)
            if(raw.length>f.maxLength||raw.any(Char::isISOControl)||f.type==PluginFieldType.BOOLEAN&&raw !in setOf("true","false"))throw BadRequestException("Invalid ${f.label}")
            val value=raw.trim();if(f.type!=PluginFieldType.SECRET||value.isNotBlank()){if(value.isEmpty())values.remove(key)else values[key]=value}
        }
        // A stored password must not be forwarded to a new server/account identity.
        if(schema.identityFields.any {effective(schema,previous)[it]!=effective(schema,values)[it]})
            fields.values.filter {it.type==PluginFieldType.SECRET&&input.values[it.key].isNullOrBlank()}.forEach {values.remove(it.key)}
        input.clearSecrets.forEach {if(!input.values[it].isNullOrBlank())throw BadRequestException("Clear or replace a credential, not both");values.remove(it)}
        values.putAll(effective(schema,values))
        validate(schema,values)
        if(input.enabled&&missing(schema,values).isNotEmpty())throw BadRequestException("Complete the required connection fields before enabling it")
        row.name=input.name.trim();row.enabled=input.enabled;row.dataJson=mapper.writeValueAsString(values)
        row.secretFieldsCsv=(protectedFields(row,schema)+schema.fields.filter {it.type==PluginFieldType.SECRET}.map {it.key}).sorted().joinToString(",")
        return view(rows.saveAndFlush(row),schema)
    }
    fun runtime(id:UUID,reference:PluginSharedSettingsReference):Map<String,String> {
        val row=row(id);return runtime(row,reference,schema(row.schemaId,false).schema)
    }
    private fun runtime(row:SharedSettingsRecord,reference:PluginSharedSettingsReference,schema:PluginSharedSettingsSchema):Map<String,String> {
        if(row.schemaId!=reference.schemaId)throw ForbiddenException("Shared connection does not match the declared schema")
        if(!schema.fields.map {it.key}.containsAll(reference.fields))throw BadRequestException("The shared connection schema changed")
        val values=decode(row)
        if(!row.enabled||missing(schema,values).isNotEmpty())throw BadRequestException("Enable and configure the shared connection in Communications or its admin section")
        return effective(schema,values).filterKeys {it in reference.fields}
    }
    fun publicRuntime(id:UUID,reference:PluginSharedSettingsReference):Map<String,String> {
        val row=row(id);val schema=schema(row.schemaId,false).schema
        return runtime(row,reference,schema).filterKeys {it !in protectedFields(row,schema)}
    }
    fun httpCredential(id:UUID,reference:PluginSharedSettingsReference,key:String,origin:String):String {
        // Read origin and credential from one row snapshot, including during concurrent profile edits.
        val row=row(id);val s=schema(row.schemaId,false).schema
        val field=s.httpOriginField ?: throw ForbiddenException("This shared connection cannot be used as an HTTP credential")
        val values=runtime(row,reference,s)
        if(key !in reference.fields||s.fields.none {it.key==key&&it.type==PluginFieldType.SECRET}||com.starter.plugins.packages.PluginBackendValidation.origin(values[field].orEmpty())!=origin)
            throw ForbiddenException("Shared credential is not permitted for this origin or field")
        return values[key]?.takeIf {it.isNotBlank()} ?: throw BadRequestException("Shared credential is not configured")
    }
    private fun decode(row:SharedSettingsRecord):Map<String,String> = row.dataJson?.let {mapper.readValue(it,object:TypeReference<Map<String,String>>() {})}.orEmpty()
    private fun effective(schema:PluginSharedSettingsSchema,values:Map<String,String>)=schema.fields.filter {it.defaultValue.isNotEmpty()}.associate {it.key to it.defaultValue}+values
    private fun missing(schema:PluginSharedSettingsSchema,values:Map<String,String>)=schema.fields.filter {it.required&&effective(schema,values)[it.key].isNullOrBlank()}.map {it.label}
    private fun validate(schema:PluginSharedSettingsSchema,values:Map<String,String>) {
        if(schema.id==IncomingEmailSchema.ID){
            values["host"]?.takeIf {it.isNotBlank()}?.let {if(!it.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")))throw BadRequestException("Use an IMAPS hostname")}
            values["port"]?.let {if(it.toIntOrNull() !in 1..65535)throw BadRequestException("Use a valid IMAPS port")}
        }
        schema.httpOriginField?.let {field->values[field]?.takeIf {it.isNotBlank()}?.let {
            if(com.starter.plugins.packages.PluginBackendValidation.origin(it)!=it.trimEnd('/'))throw BadRequestException("Use a canonical HTTPS origin without a path or query")
        }}
    }
    private fun protectedFields(row:SharedSettingsRecord,schema:PluginSharedSettingsSchema)=row.secretFieldsCsv.split(',').filter {it.isNotBlank()}.toSet()+schema.fields.filter {it.type==PluginFieldType.SECRET}.map {it.key}
    private fun view(row:SharedSettingsRecord,schema:PluginSharedSettingsSchema):SharedProfileView {
        val values=decode(row);val secret=protectedFields(row,schema)
        val publicKeys=schema.fields.filter {it.type!=PluginFieldType.SECRET}.map {it.key}.toSet()
        return SharedProfileView(row.id,row.schemaId,row.name,row.enabled,effective(schema,values).filterKeys {it in publicKeys&&it !in secret},secret.filter { !values[it].isNullOrBlank() }.toSet(),missing(schema,values),row.revision)
    }
}
