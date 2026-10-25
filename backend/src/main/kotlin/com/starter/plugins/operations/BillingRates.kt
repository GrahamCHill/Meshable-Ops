package com.starter.plugins.operations

import com.starter.exception.*
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.math.BigDecimal

@Service
class BillingRates(private val store:OperationsStore,private val access:OperationsAccess,private val users:UserRepository,private val visibility:TimeVisibility) {
    fun list(email:String):Map<String,Any> {
        access.admin(email,"billing-items")
        return mapOf("rates" to store.list("billing-items","kind='RATE'").map {mapOf("id" to it.id,"rate" to store.decode(it,DeveloperRate::class.java),"revision" to it.revision)},
            "assignments" to store.list("billing-items","kind='ASSIGNMENT'").map {mapOf("id" to it.id,"assignment" to store.decode(it,RateAssignment::class.java),"revision" to it.revision)})
    }
    @Transactional fun rate(email:String,id:UUID?,input:RateInput):OpsRow {
        val actor=access.admin(email,"billing-items",true);val r=input.rate
        OperationsValidation.text(r.name,"rate name",100);validateAmount(r.amount,r.currency)
        store.lock("billing-rate:${r.roleId}")
        if(r.enabled&&store.list("billing-items","kind='RATE'").filter {it.id!=id}.any {store.decode(it,DeveloperRate::class.java).let {other->other.roleId==r.roleId&&other.enabled}})throw ConflictException("Disable the existing rate before enabling another rate for this role")
        val row=id?.let {store.get("billing-items",it).also {old->if(old.kind!="RATE")throw NotFoundException("Rate unavailable")}}
        return store.save("billing-items",row?.ownerId ?: actor.id,null,"RATE",r,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
    }
    @Transactional fun assignment(email:String,id:UUID?,input:AssignmentInput):OpsRow {
        access.admin(email,"billing-items",true);val a=input.assignment
        val user=users.findById(a.userId).orElse(null)
        if(user?.let {it.active&&it.role!="EXTERNAL"}!=true)throw BadRequestException("Choose an active internal member")
        if(store.list("billing-items","kind='RATE'").none {store.decode(it,DeveloperRate::class.java).roleId==a.roleId})throw BadRequestException("Choose a billing role")
        store.lock("rate-assignment:${a.userId}")
        if(a.enabled&&store.list("billing-items","kind='ASSIGNMENT' AND owner_id=?",a.userId).filter {it.id!=id}.any {store.decode(it,RateAssignment::class.java).enabled})throw ConflictException("Disable the member's existing assignment first")
        id?.let {val old=store.get("billing-items",it);if(old.kind!="ASSIGNMENT"||old.ownerId!=a.userId)throw NotFoundException("Assignment unavailable")}
        return store.save("billing-items",a.userId,null,"ASSIGNMENT",a,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
    }
    fun project(email:String,project:UUID):List<Map<String,Any?>> {
        val (actor,p)=access.project(email,"billing-items",project);visibility.requireSenior(actor,p)
        val rows=store.list("billing-items","kind='PROJECT_RATE' AND project_id=?",project)
        return access.projects.members(p).mapNotNull {id->users.findById(id).orElse(null)?.takeIf {it.active&&it.role!="EXTERNAL"}?.let {user->
            val row=rows.firstOrNull {it.ownerId==id}
            val rate=row?.let {store.decode(it,ProjectMemberRate::class.java)}
            mapOf("userId" to id,"name" to (user.name?.takeIf {it.isNotBlank()} ?: user.email),"rate" to rate,"revision" to (row?.revision ?: -1),"goingRate" to resolve(id,project))
        }}
    }
    @Transactional fun projectSave(email:String,project:UUID,input:ProjectMemberRateInput):OpsRow {
        val (actor,p)=access.project(email,"billing-items",project,false,true);visibility.requireSenior(actor,p)
        val r=input.rate;validateAmount(r.amount,r.currency)
        if(r.userId !in access.projects.members(p))throw BadRequestException("Choose a member of this project")
        val user=users.findById(r.userId).orElse(null)
        if(user?.let {it.active&&it.role!="EXTERNAL"}!=true)throw BadRequestException("Choose an active internal project member")
        store.lock("project-billing-rate:$project:${r.userId}")
        return store.save("billing-items",r.userId,project,"PROJECT_RATE",r,store.stable("project-billing-rate:$project:${r.userId}"),input.revision)
    }
    fun resolve(user:UUID,project:UUID):DeveloperRate? {
        if(!access.enabled("billing-items"))return null
        val local=store.list("billing-items","kind='PROJECT_RATE' AND project_id=? AND owner_id=?",project,user).firstOrNull()
        if(local!=null){val r=store.decode(local,ProjectMemberRate::class.java);return if(r.enabled)DeveloperRate("Project member",r.amount,r.currency,local.id)else null}
        // Legacy date-based rows resolve to the most recently saved enabled value.
        val assignment=store.list("billing-items","kind='ASSIGNMENT' AND owner_id=?",user).map {store.decode(it,RateAssignment::class.java)}.firstOrNull {it.enabled} ?: return null
        return store.list("billing-items","kind='RATE'").map {store.decode(it,DeveloperRate::class.java)}.firstOrNull {it.roleId==assignment.roleId&&it.enabled}
    }
    companion object {
        fun validateAmount(amount:BigDecimal,currency:String){OperationsValidation.currency(currency);if(amount.signum()<0||amount.scale()>4||amount>BigDecimal("10000000"))throw BadRequestException("Choose a nonnegative bounded hourly rate")}
    }
}
