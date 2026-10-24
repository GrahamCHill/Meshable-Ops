package com.starter.documents

import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class DocumentAccess(private val users: UserRepository, private val documents: GeneratedDocumentRepository) {
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun actor(email: String): User = users.findByEmail(email).orElse(null)?.takeIf { it.active } ?: throw ForbiddenException("Active account required")
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun admin(email: String) = actor(email).also { if (it.role != "ADMIN") throw ForbiddenException("Administrator access required") }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun document(actor: User, id: UUID): GeneratedDocument = documents.findById(id).orElse(null)
        ?.takeIf { actor.role == "ADMIN" || it.ownerId == actor.id } ?: throw NotFoundException("Document unavailable")
    fun references(actor: User, ids: List<UUID>) {
        if (ids.isNotEmpty() && documents.visibleCount(ids, actor.id, actor.role == "ADMIN") != ids.distinct().size.toLong()) throw NotFoundException("A workflow document is unavailable")
    }
}
