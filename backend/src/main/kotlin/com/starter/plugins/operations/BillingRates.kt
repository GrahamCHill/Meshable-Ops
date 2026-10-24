package com.starter.plugins.operations

import com.starter.exception.*
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class BillingRates(private val store:OperationsStore,private val access:OperationsAccess,private val users:UserRepository) {
    fun list(email:String):Map<String,Any> {
        access.admin(email,"billing-items")
        return mapOf("rates" to store.list("billing-items","kind='RATE'").map {mapOf("id" to it.id,"rate" to store.decode(it,DeveloperRate::class.java),"revision" to it.revision)},
            "assignments" to store.list("billing-items","kind='ASSIGNMENT'").map {mapOf("id" to it.id,"assignment" to store.decode(it,RateAssignment::class.java),"revision" to it.revision)})
    }
    @Transactional fun rate(email:String,id:UUID?,input:RateInput):OpsRow {
        val actor=access.admin(email,"billing-items",true);val r=input.rate
        OperationsValidation.text(r.name,"rate name",100);OperationsValidation.currency(r.currency)
        if(r.amount.signum()<0 || r.amount.scale()>4 || r.amount>java.math.BigDecimal("10000000") || r.effectiveTo?.isBefore(r.effectiveFrom)==true) throw BadRequestException("Choose a nonnegative bounded rate and effective dates")
        store.lock("billing-rate:${r.roleId}")
        val others=store.list("billing-items","kind='RATE'").filter {it.id!=id}.map {store.decode(it,DeveloperRate::class.java)}
        if(others.any {it.roleId==r.roleId && overlaps(it.effectiveFrom,it.effectiveTo,r.effectiveFrom,r.effectiveTo)}) throw ConflictException("Rates for this developer role cannot overlap")
        val row=id?.let {store.get("billing-items",it).also {old->if(old.kind!="RATE")throw NotFoundException("Rate unavailable")}}
        return store.save("billing-items",row?.ownerId ?: actor.id,null,"RATE",r,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
    }
    @Transactional fun assignment(email:String,id:UUID?,input:AssignmentInput):OpsRow {
        val actor=access.admin(email,"billing-items",true);val a=input.assignment
        val user=users.findById(a.userId).orElse(null)
        if(user?.let {it.active && it.role!="EXTERNAL"}!=true || a.effectiveTo?.isBefore(a.effectiveFrom)==true) throw BadRequestException("Choose an active internal member and effective dates")
        if(store.list("billing-items","kind='RATE'").none {store.decode(it,DeveloperRate::class.java).roleId==a.roleId})throw BadRequestException("Choose a developer billing role")
        store.lock("rate-assignment:${a.userId}")
        if(store.list("billing-items","kind='ASSIGNMENT' AND owner_id=?",a.userId).filter {it.id!=id}.any {store.decode(it,RateAssignment::class.java).let {old->overlaps(old.effectiveFrom,old.effectiveTo,a.effectiveFrom,a.effectiveTo)}}) throw ConflictException("A member may have only one billing role on a date")
        id?.let {if(store.get("billing-items",it).kind!="ASSIGNMENT")throw NotFoundException("Assignment unavailable")}
        return store.save("billing-items",a.userId,null,"ASSIGNMENT",a,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
    }
    fun resolve(user:UUID,date:LocalDate):DeveloperRate? {
        if(!access.enabled("billing-items"))return null
        val assignment=store.list("billing-items","kind='ASSIGNMENT' AND owner_id=?",user).map {store.decode(it,RateAssignment::class.java)}.singleOrNull {contains(it.effectiveFrom,it.effectiveTo,date)} ?: return null
        return store.list("billing-items","kind='RATE'").map {store.decode(it,DeveloperRate::class.java)}.singleOrNull {it.roleId==assignment.roleId && contains(it.effectiveFrom,it.effectiveTo,date)}
    }
    companion object {
        fun contains(from:LocalDate,to:LocalDate?,date:LocalDate)=date>=from && (to==null||date<=to)
        fun overlaps(a:LocalDate,b:LocalDate?,c:LocalDate,d:LocalDate?)=a<=(d ?: LocalDate.MAX) && c<=(b ?: LocalDate.MAX)
    }
}
