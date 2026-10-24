package com.starter.plugins.documents

import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class DocumentAccess(private val users: UserRepository, private val documents: GeneratedDocumentRepository, private val modules: com.starter.plugins.BundledModuleAccess) {
    fun library(actor: User, write: Boolean = false) = modules.check("documents", actor, "generated_documents", "documents", write)
    fun template(actor: User, admin: Boolean = false, write: Boolean = false) = modules.check("documents", actor, "document_templates", if (admin) "admin.templates" else "documents", write)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun actor(email: String): User = users.findByEmail(email).orElse(null)?.takeIf { it.active } ?: throw ForbiddenException("Active account required")
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun admin(email: String) = actor(email).also { if (!com.starter.access.Roles.admin(it.role)) throw ForbiddenException("Administrator access required") }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun document(actor: User, id: UUID): GeneratedDocument {
        library(actor)
        return documents.findById(id).orElse(null)?.takeIf { com.starter.access.Roles.admin(actor.role) || it.ownerId == actor.id } ?: throw NotFoundException("Document unavailable")
    }
    fun references(actor: User, ids: List<UUID>) {
        if (ids.isNotEmpty()) library(actor)
        if (ids.isNotEmpty() && documents.visibleCount(ids, actor.id, com.starter.access.Roles.admin(actor.role)) != ids.distinct().size.toLong()) throw NotFoundException("A workflow document is unavailable")
    }
}
