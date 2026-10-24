package com.starter.service

import com.starter.dto.VectorSearchRequest
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.repository.postgres.ItemRepository
import com.starter.service.storage.normalizeStorageFolder
import org.springframework.stereotype.Service
import java.util.UUID

data class SearchScope(val folderId: UUID? = null, val pathPrefix: String = "")

@Service
class SearchScopeResolver(private val items: ItemRepository, private val access: com.starter.access.AccessControlService? = null) {
    fun resolve(request: VectorSearchRequest): SearchScope {
        if (request.folderId == null) {
            if (request.path.isNotBlank()) throw BadRequestException("A search path requires a shared folder")
            return SearchScope()
        }
        access?.requireStorage(request.folderId, request.path, true)
        val folder = items.findById(request.folderId).orElseThrow { NotFoundException("Storage root not found") }
        if (!folder.accessible || folder.storageConnection?.enabled == false) throw NotFoundException("Storage root not found")
        return SearchScope(request.folderId, normalizeStorageFolder(request.path))
    }
}
