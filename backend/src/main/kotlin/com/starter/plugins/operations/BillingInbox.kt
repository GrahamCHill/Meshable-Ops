package com.starter.plugins.operations

import com.starter.exception.*
import com.starter.plugins.PluginConfigurationService
import com.starter.notification.NotificationPublisher
import com.starter.security.SecretEncryption
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.Properties
import java.util.UUID

@Service
class BillingInbox(private val store:OperationsStore,private val access:OperationsAccess,private val profiles:BillingProfiles,private val config:PluginConfigurationService,
    private val encryption:SecretEncryption,private val notifications:NotificationPublisher,private val txManager:PlatformTransactionManager) {
    fun list(email:String,project:UUID):List<Map<String,Any>> {
        profiles.authorize(email,project)
        return store.list("billing","project_id=? AND kind='REPLY'",project).map {row->mapOf("id" to row.id,"reply" to store.decode(row,BillingReply::class.java).copy(body=encryption.decrypt(store.secret("billing",row.id)).orEmpty()),"revision" to row.revision)}
    }
    fun poll(email:String):ImportResult {
        access.admin(email,"billing",true);val c=config.current("billing")
        if(c["inboxEnabled"]!="true")throw BadRequestException("Enable the dedicated billing inbox first")
        val incoming=config.shared("billing","incoming-email")
        val host=incoming["host"].orEmpty();val username=incoming["username"].orEmpty();val password=incoming["password"].orEmpty();val folderName=incoming["folder"] ?: "INBOX"
        if(host.isBlank()||username.isBlank()||password.isBlank())throw BadRequestException("Configure the shared IMAPS account in Communications")
        encryption.requireConfigured()
        // IMAPS only, normal certificate+hostname verification, bounded timeouts, no remote HTML or attachments.
        val properties=Properties().apply {setProperty("mail.store.protocol","imaps");setProperty("mail.imaps.ssl.enable","true");setProperty("mail.imaps.ssl.checkserveridentity","true");setProperty("mail.imaps.connectiontimeout","10000");setProperty("mail.imaps.timeout","10000");setProperty("mail.imaps.writetimeout","10000");setProperty("mail.imaps.peek","true");setProperty("mail.imaps.partialfetch","true")}
        var imported=0;var skipped=0
        try {
            Session.getInstance(properties).getStore("imaps").use {mail->
                mail.connect(host,incoming["port"]?.toIntOrNull() ?: 993,username,password)
                val folder=mail.getFolder(folderName);folder.open(Folder.READ_ONLY)
                try {
                    val uid=folder as? UIDFolder ?: throw BadRequestException("Billing IMAPS requires stable message UIDs")
                    val cursorId=store.stable("billing-inbox-cursor:$host:$username:$folderName:${uid.uidValidity}")
                    val last=store.list("billing","id=? AND kind='CURSOR'",cursorId).firstOrNull()?.let {store.mapper.readTree(it.dataJson).path("uid").asLong()} ?: 0L
                    val end=minOf(last+100,uid.uidNext-1)
                    // A durable UID cursor drains a backlog in bounded batches, including after downtime.
                    val messages=if(end<=last)emptyArray()else uid.getMessagesByUID(last+1,end).filterNotNull().toTypedArray()
                    for(message in messages) {
                        if(config.current("billing")!=c||config.shared("billing","incoming-email")!=incoming)throw ConflictException("Billing inbox settings changed")
                        val subject=message.subject.orEmpty().take(500)
                        val token=Regex("\\[MO-BILL-([0-9a-fA-F-]{36})]").find(subject)?.groupValues?.get(1)?.let {runCatching {UUID.fromString(it)}.getOrNull()} ?: continue
                        if(message.size>262144){skipped++;continue}
                        val match=store.list("billing","kind='DOCUMENT' AND CAST(data_json AS jsonb)->>'replyToken'=?",token.toString()).singleOrNull() ?: continue
                        val d=store.decode(match,BillingSnapshot::class.java)
                        val sender=(message.from?.singleOrNull() as? InternetAddress)?.address.orEmpty()
                        if(!sender.equals(d.profile.billingEmail,true)){skipped++;continue}
                        val text=plain(message,0)?.take(16000) ?: "No plain-text body. Inspect the original billing mailbox."
                        val key=store.stable("billing-reply:$host:$username:$folderName:${uid.uidValidity}:${uid.getUID(message)}")
                        val recipients=setOf(profiles.profile(d.clientId).businessManagerId,access.projects.require(d.projectId,access.actors.current(email)).leadId)
                        val category=if(Regex("(?i)cancel(lation|ation)?|terminate|stop (the |my )?(service|subscription)").containsMatchIn(text))"CANCELLATION_REQUEST" else "INFORMATION"
                        val reply=BillingReply(match.id,d.clientId,sender,subject,"",message.receivedDate?.toInstant() ?: Instant.now(),category,recipients)
                        val fresh=TransactionTemplate(txManager).execute {store.lock("billing-inbox:$key");if(store.list("billing","id=?",key).isNotEmpty())false else {store.save("billing",match.ownerId,match.projectId,"REPLY",reply,key);store.setSecret("billing",key,encryption.encrypt(text));true}}==true
                        // Repeated notifications use the same deduplication key; a missed route can be retried safely.
                        recipients.forEach {target->runCatching {notifications.publish("billing.reply",target,mapOf("subject" to "Billing reply: ${d.number}","message" to "$category from $sender for ${d.projectName}.\n${text.take(6000)}\nOpen Invoices & quotes → Billing replies. Requests do not automatically cancel work or invoices."),"billing-reply:$key:$target",splitLongText=true)}}
                        if(fresh)imported++ else skipped++
                    }
                    if(end>last)TransactionTemplate(txManager).executeWithoutResult {
                        store.lock("billing-cursor:$cursorId")
                        val old=store.list("billing","id=?",cursorId).firstOrNull()
                        val previous=old?.let {store.mapper.readTree(it.dataJson).path("uid").asLong()} ?: 0L
                        if(end>previous)store.save("billing",old?.ownerId ?: access.actors.current(email).id,null,"CURSOR",mapOf("uid" to end),cursorId,old?.revision ?: -1)
                    }
                } finally {folder.close(false)}
            }
        } catch(e:BadRequestException){throw e} catch(e:ConflictException){throw e} catch(_:Exception){throw BadRequestException("Billing inbox could not be read. Check TLS, credentials, folder and server access; no mailbox messages were changed.")}
        return ImportResult(imported,skipped)
    }
    companion object {
        fun plain(part:Part,depth:Int):String? {
            if(depth>5||part.size>262144||part.disposition?.equals(Part.ATTACHMENT,true)==true)return null
            if(part.isMimeType("text/plain"))return part.inputStream.use {String(it.readNBytes(16001),Charsets.UTF_8).take(16000)}
            if(part.isMimeType("multipart/*")){val content=part.content as? Multipart ?: return null;for(i in 0 until minOf(20,content.count)){plain(content.getBodyPart(i),depth+1)?.let {return it}}}
            return null
        }
    }
}
