package com.starter.access

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** A new persistence context prevents long PDF/AI requests from reusing stale policy or account entities. */
@Service
class AccessPolicyReader(private val policies: AccessPolicyRepository, private val users: UserRepository, private val mapper: ObjectMapper,
    private val items: com.starter.repository.postgres.ItemRepository, private val settings: com.starter.repository.postgres.AppSettingsRepository) {
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun policy() = policies.findById(1).orElse(null)?.let { mapper.readValue(it.policyJson, AccessPolicy::class.java) } ?: AccessPolicy()
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun user(email: String) = users.findByEmail(email).orElse(null)?.takeIf { it.active }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun locations(): List<StorageLocation> {
        val defaults = settings.findTopByOrderByCreatedAtAsc()
        return items.findAll().mapNotNull { item ->
            val connection = item.storageConnection
            val provider = connection?.provider ?: defaults?.storageProvider ?: return@mapNotNull null
            val bucket = connection?.bucket ?: defaults?.storageBucket ?: return@mapNotNull null
            val endpoint = (if (connection == null) defaults?.storageEndpoint else connection.endpoint).orEmpty().trimEnd('/')
            StorageLocation(item.id, provider, bucket, endpoint, com.starter.service.storage.normalizeStorageFolder(item.storagePrefix ?: "${defaults?.storageRootPath?.ifBlank { "folders" } ?: "folders"}/${item.id}/"))
        }
    }
}
