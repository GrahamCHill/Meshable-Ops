package com.starter.plugins.documents

import com.starter.notification.*
import org.springframework.stereotype.Component

@Component
class DocumentNotifications : NotificationDefinitionProvider {
    override val pluginId = "documents"
    override val namespace = "documents."
    private val common = setOf("applicationName", "recipientName", "subject", "message")
    override fun definitions() = listOf(
        NotificationDefinition("documents.email","Document emails","Explicit sends of generated PDF attachments.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}")
    )
}
