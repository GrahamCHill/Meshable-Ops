package com.starter.service

import com.starter.repository.mongo.ActivityLogRepository
import com.starter.repository.mongo.EventRepository
import com.starter.repository.postgres.AppSettingsRepository
import com.starter.repository.postgres.DashboardEmbedConfigRepository
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class BackupExportService(
    private val userRepository: UserRepository,
    private val itemRepository: ItemRepository,
    private val storageDocumentRepository: StorageDocumentRepository,
    private val dashboardEmbedConfigRepository: DashboardEmbedConfigRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val eventRepository: EventRepository,
    private val activityLogRepository: ActivityLogRepository,
) {

    @Transactional(readOnly = true)
    fun export(): Map<String, Any?> {
        val users = userRepository.findAllByOrderByRoleAscCreatedAtAsc().map { user ->
            mapOf(
                "id" to user.id.toString(),
                "email" to user.email,
                "username" to user.username,
                "name" to user.name,
                "contactNumber" to user.contactNumber,
                "role" to user.role,
                "active" to user.active,
                "createdAt" to user.createdAt.toString(),
                "updatedAt" to user.updatedAt.toString(),
            )
        }

        val items = itemRepository.findAllByOrderByCreatedAtDesc().map { item ->
            mapOf(
                "id" to item.id.toString(),
                "userId" to item.user.id.toString(),
                "title" to item.title,
                "description" to item.description,
                "tags" to item.tags.toList(),
                "storagePrefix" to item.storagePrefix,
                "createdAt" to item.createdAt.toString(),
                "updatedAt" to item.updatedAt.toString(),
            )
        }

        val storageDocuments = storageDocumentRepository.findAll().map { document ->
            mapOf(
                "id" to document.id.toString(),
                "folderId" to document.item.id.toString(),
                "storagePath" to document.storagePath,
                "fileName" to document.fileName,
                "contentType" to document.contentType,
                "sizeBytes" to document.sizeBytes,
                "storageLastModified" to document.storageLastModified,
                "processingStatus" to document.processingStatus,
                "sourceChecksum" to document.sourceChecksum,
                "embeddingModel" to document.embeddingModel,
                "vectorizedAt" to document.vectorizedAt?.toString(),
                "createdAt" to document.createdAt.toString(),
                "updatedAt" to document.updatedAt.toString(),
            )
        }

        val dashboardEmbeds = dashboardEmbedConfigRepository.findAllByOrderBySortOrderAscCreatedAtAsc().map { config ->
            mapOf(
                "id" to config.id.toString(),
                "label" to config.label,
                "description" to config.description,
                "dashboardId" to config.dashboardId,
                "guestTokenResourceId" to config.guestTokenResourceId,
                "sortOrder" to config.sortOrder,
                "enabled" to config.enabled,
                "allowedRoles" to config.allowedRoles.toList(),
                "createdAt" to config.createdAt.toString(),
                "updatedAt" to config.updatedAt.toString(),
            )
        }

        val settings = appSettingsRepository.findTopByOrderByCreatedAtAsc()?.let { setting ->
            mapOf(
                "id" to setting.id.toString(),
                "storageProvider" to setting.storageProvider,
                "storageBucket" to setting.storageBucket,
                "storageRegion" to setting.storageRegion,
                "storageEndpoint" to setting.storageEndpoint,
                "storageAccessKey" to setting.storageAccessKey,
                "storageSecretKey" to setting.storageSecretKey,
                "storageRootPath" to setting.storageRootPath,
                "vectorReindexEnabled" to setting.vectorReindexEnabled,
                "vectorReindexIntervalMinutes" to setting.vectorReindexIntervalMinutes,
                "lastVectorReindexAt" to setting.lastVectorReindexAt?.toString(),
                "lastVectorReindexStatus" to setting.lastVectorReindexStatus,
                "createdAt" to setting.createdAt.toString(),
                "updatedAt" to setting.updatedAt.toString(),
            )
        }

        val events = eventRepository.findAll().sortedByDescending { it.createdAt }.map { event ->
            mapOf(
                "id" to event.id,
                "userId" to event.userId,
                "type" to event.type,
                "payload" to event.payload,
                "createdAt" to event.createdAt.toString(),
            )
        }

        val activityLogs = activityLogRepository.findAll().sortedByDescending { it.timestamp }.map { log ->
            mapOf(
                "id" to log.id,
                "userId" to log.userId,
                "action" to log.action,
                "resourceType" to log.resourceType,
                "resourceId" to log.resourceId,
                "ipAddress" to log.ipAddress,
                "userAgent" to log.userAgent,
                "timestamp" to log.timestamp.toString(),
            )
        }

        return mapOf(
            "exportedAt" to Instant.now().toString(),
            "format" to "superset-docker-app-backup-v1",
            "data" to mapOf(
                "users" to users,
            "items" to items,
            "storageDocuments" to storageDocuments,
                "dashboardEmbedConfigs" to dashboardEmbeds,
                "appSettings" to settings,
                "events" to events,
                "activityLogs" to activityLogs,
            )
        )
    }
}
