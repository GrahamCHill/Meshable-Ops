package com.starter.plugins.operations

import com.starter.access.Roles
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.projects.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class EffectiveBillingItem(val keyId:UUID,val itemId:UUID,val item:CatalogItem,val linkId:UUID,val pendingApproval:Boolean)

@Service
class BillingCatalog(private val store:OperationsStore,private val access:OperationsAccess,private val clients:ProjectService,private val visibility:TimeVisibility) {
    private fun editor(email:String,write:Boolean=false):User {
        val actor=access.actor(email,"billing-items",write)
        if(!ProjectCreationRules.allowed(actor,store.jdbc))throw ForbiddenException("The shared billing catalog is managed by administrators, Business Managers and project leaders")
        return actor
    }
    fun catalog(email:String):List<Map<String,Any>> {
        val actor=editor(email)
        return store.list("billing-items","kind='ITEM'").map {mapOf("id" to it.id,"item" to store.decode(it,CatalogItem::class.java),"revision" to it.revision,"canEdit" to (Roles.admin(actor.role)||it.ownerId==actor.id))}
    }
    @Transactional fun save(email:String,id:UUID?,input:CatalogInput):OpsRow {
        val actor=editor(email,true);validate(input.item)
        val old=id?.let {store.get("billing-items",it).also {r->if(r.kind!="ITEM")throw NotFoundException("Catalog item unavailable");if(r.ownerId!=actor.id&&!Roles.admin(actor.role))throw ForbiddenException("Only the catalog item's owner or an administrator can edit it")}}
        val row=store.save("billing-items",old?.ownerId ?: actor.id,null,"ITEM",input.item,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
        // Older callers can create and link in one operation; the catalog definition remains global.
        (input.projectId ?: old?.projectId)?.let {link(email,BillingLinkInput(row.id,projectId=it))}
        return row
    }
    private fun scope(email:String,project:UUID?,client:UUID?,write:Boolean):User {
        val actor=access.actor(email,"billing-items",write)
        if((project==null)==(client==null))throw BadRequestException("Link an item to one project or one client")
        if(project!=null){val (_,p)=access.project(email,"billing-items",project,false,write);visibility.requireSenior(actor,p)}
        else clients.requireClient(email,client!!,true)
        return actor
    }
    @Transactional fun link(email:String,input:BillingLinkInput):OpsRow {
        val actor=scope(email,input.projectId,input.clientId,true);val item=store.get("billing-items",input.itemId)
        if(item.kind!="ITEM")throw NotFoundException("Catalog item unavailable")
        val id=store.stable("billing-link:${input.itemId}:${input.projectId ?: input.clientId}")
        store.lock("billing-link:$id")
        if(store.list("billing-items","id=?",id).isNotEmpty())throw ConflictException("This item is already linked to this scope")
        val inherited=input.projectId?.let {access.projects.require(it,actor).clientId}?.let {client->linksForClient(client).firstOrNull {store.decode(it,BillingItemLink::class.java).itemId==item.id}}?.let {store.decode(it,BillingItemLink::class.java)}
        // A project link cannot bypass an existing client's agreed base price by relinking a newer catalog revision.
        val base=inherited?.baseItem ?: store.decode(item,CatalogItem::class.java)
        val value=BillingItemLink(item.id,input.projectId,input.clientId,base,inherited?.baseRevision ?: item.revision,
            approvedOverrides=if(input.projectId!=null)inherited?.approvedOverrides?.filterKeys {it==input.projectId}.orEmpty()else emptyMap())
        return store.save("billing-items",actor.id,input.projectId,"LINK",value,id)
    }
    fun links(email:String,project:UUID?,client:UUID?):List<Map<String,Any?>> {
        val actor=scope(email,project,client,false)
        val p=project?.let {access.projects.require(it,actor)}
        val rows=if(project!=null)store.list("billing-items","kind='LINK' AND project_id=?",project)+p?.clientId?.let(::linksForClient).orEmpty() else linksForClient(client!!)
        return rows.map {row->val l=store.decode(row,BillingItemLink::class.java)
            mapOf("id" to row.id,"revision" to row.revision,"link" to l,"item" to (project?.let {l.approvedOverrides[it]} ?: l.baseItem),"scope" to if(l.projectId!=null)"PROJECT"else"CLIENT", "pendingApproval" to (l.proposedItem!=null&&(project==null||l.approvals[project]!=l.proposalVersion)),"canApprove" to (p!=null&&canApprove(actor,p)))
        }
    }
    @Transactional fun propose(email:String,id:UUID,input:BillingOverrideInput):OpsRow {
        val row=store.get("billing-items",id);if(row.kind!="LINK")throw NotFoundException("Item link unavailable")
        val link=store.decode(row,BillingItemLink::class.java);scope(email,link.projectId,link.clientId,true);validate(input.item)
        return store.save("billing-items",row.ownerId,row.projectId,"LINK",link.copy(proposedItem=input.item,proposalVersion=link.proposalVersion+1),row.id,input.revision)
    }
    @Transactional fun approve(email:String,id:UUID,input:BillingOverrideApproval):OpsRow {
        val (actor,p)=access.project(email,"billing-items",input.projectId,false,true)
        if(!canApprove(actor,p))throw ForbiddenException("A project MANAGER or administrator must approve billing overrides")
        store.lock("billing-link-approval:$id")
        val row=store.get("billing-items",id);if(row.kind!="LINK")throw NotFoundException("Item link unavailable")
        val l=store.decode(row,BillingItemLink::class.java)
        if(l.projectId!=p.id&&(l.projectId!=null||l.clientId!=p.clientId))throw NotFoundException("Item link unavailable for this project")
        if(l.proposedItem==null||input.proposalVersion!=l.proposalVersion)throw ConflictException("The billing proposal changed or is unavailable")
        val value=l.copy(approvedOverrides=l.approvedOverrides+(p.id to l.proposedItem),approvals=l.approvals+(p.id to l.proposalVersion))
        return store.save("billing-items",row.ownerId,row.projectId,"LINK",value,row.id,input.revision)
    }
    fun effective(project:ProjectRecord):List<EffectiveBillingItem> {
        val rows=store.list("billing-items","kind='LINK' AND project_id=?",project.id)+project.clientId?.let(::linksForClient).orEmpty()
        val grouped=rows.groupBy {store.decode(it,BillingItemLink::class.java).itemId}
        return grouped.map {(itemId,records)->
            val projectRow=records.firstOrNull {it.projectId==project.id};val clientRow=records.firstOrNull {it.projectId==null}
            val selected=projectRow ?: clientRow!!;val l=store.decode(selected,BillingItemLink::class.java)
            EffectiveBillingItem(clientRow?.id ?: selected.id,itemId,l.approvedOverrides[project.id] ?: l.baseItem,selected.id,l.proposedItem!=null&&l.approvals[project.id]!=l.proposalVersion)
        }+store.list("billing-items","kind='ITEM' AND project_id=?",project.id).map {EffectiveBillingItem(it.id,it.id,store.decode(it,CatalogItem::class.java),it.id,false)}
    }
    private fun linksForClient(id:UUID)=store.list("billing-items","kind='LINK' AND project_id IS NULL AND CAST(data_json AS jsonb)->>'clientId'=?",id.toString())
    private fun canApprove(actor:User,p:ProjectRecord)=Roles.admin(actor.role)||access.projects.role(actor,p)==ProjectRole.MANAGER||access.projects.details(p).members.any {it.userId==actor.id&&it.role==ProjectRole.MANAGER}
    companion object {fun validate(item:CatalogItem) {
        OperationsValidation.text(item.name,"item name",200);OperationsValidation.currency(item.currency)
        if(item.amount.signum()<0||item.amount>BigDecimal("1000000000")||item.amount.scale()>6||item.quantity.signum()<=0||item.quantity>BigDecimal("100000000")||item.quantity.scale()>6||item.activeTo?.isBefore(item.activeFrom)==true)throw BadRequestException("Choose valid item prices, quantities and effective dates")
    }}
}
