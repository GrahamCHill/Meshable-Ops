package com.starter.notification

import com.starter.dto.EmailDeliveryResult
import com.starter.exception.NotFoundException
import com.starter.repository.postgres.UserRepository
import com.starter.security.RateLimits
import org.springframework.stereotype.Service
import java.util.UUID

/** Call only after the feature/plugin has authorized the business action and intended recipient. */
@Service
class NotificationPublisher(private val catalogue: NotificationCatalogue,private val preferences: NotificationPreferencesService,
    private val users: UserRepository,private val notifications: NotificationService,private val rates: RateLimits) {
    fun internalMembers(): List<Map<String, Any>> = users.findAll().filter { it.active && it.role != "EXTERNAL" }.map { mapOf("id" to it.id, "name" to (it.name ?: it.username)) }
    fun publish(event: String,userId: UUID,values: Map<String,String>,key: String=UUID.randomUUID().toString(),selectedMethods: Set<String>?=null): Map<String,EmailDeliveryResult> {
        val actor=users.findById(userId).orElse(null)?.takeIf {it.active} ?: throw NotFoundException("Notification recipient unavailable")
        val definition=catalogue.get(event)
        if(definition.category !in setOf(NotificationCategory.GENERAL,NotificationCategory.WORKFLOW)) throw com.starter.exception.BadRequestException("Use the verified OTP or explicit attachment delivery entry point")
        val preferred=preferences.methodsFor(actor,definition)
        val methods=selectedMethods?.intersect(preferred) ?: preferred
        val target=NotificationTarget(actor.id,actor.email,actor.contactNumber.orEmpty(),actor.name ?: actor.username)
        return methods.associateWith {method ->
            if(notifications.availableFor(event,method)) rates.check("notification-$event",actor.id.toString(),60,3600)
            notifications.deliver(event,method,target,values,key)
        }
    }
}
