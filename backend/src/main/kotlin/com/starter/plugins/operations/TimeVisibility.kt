package com.starter.plugins.operations

import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.projects.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class TimeVisibility(private val store:OperationsStore,private val access:OperationsAccess) {
    fun senior(actor:User,project:ProjectRecord):Boolean {
        val role=access.projects.role(actor,project) ?: return false
        if(role!=ProjectRole.MEMBER)return true
        if(actor.role!=com.starter.access.Roles.BUSINESS_MANAGER||project.clientId==null)return false
        return store.list("billing","id=? AND kind='PROFILE'",project.clientId!!).firstOrNull()?.let {store.decode(it,BillingProfile::class.java).businessManagerId==actor.id}==true
    }
    fun requireSenior(actor:User,project:ProjectRecord) {if(!senior(actor,project))throw ForbiddenException("Project time detail requires a leader or the client's assigned Business Manager")}
    fun totals(email:String,project:UUID,from:LocalDate,to:LocalDate):Map<String,Any> {
        val (actor,p)=access.project(email,"timesheets",project);OperationsValidation.dates(from,to)
        val rows=store.jdbc.query("""SELECT CAST(data_json AS jsonb)->>'source' AS source,CAST(data_json AS jsonb)->>'status' AS status,
            SUM((CAST(data_json AS jsonb)->>'seconds')::bigint) AS seconds,COUNT(*) AS entries
            FROM plugin_timesheets_records WHERE kind='ENTRY' AND project_id=? AND CAST(data_json AS jsonb)->>'status'<>'REJECTED'
            AND (CAST(data_json AS jsonb)->>'start')::timestamptz>=? AND (CAST(data_json AS jsonb)->>'start')::timestamptz<?
            GROUP BY source,status""",{rs,_->mapOf("source" to rs.getString("source"),"status" to rs.getString("status"),"seconds" to rs.getLong("seconds"),"entries" to rs.getLong("entries"))},project,java.sql.Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant()),java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
        return mapOf("projectId" to project,"totalSeconds" to rows.sumOf {(it["seconds"] as Long)},"approvedSeconds" to rows.filter {it["status"]=="APPROVED"}.sumOf {it["seconds"] as Long},"breakdown" to rows,"canReview" to senior(actor,p))
    }
}
