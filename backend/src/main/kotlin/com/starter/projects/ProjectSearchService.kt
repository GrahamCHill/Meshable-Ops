package com.starter.projects

import com.starter.dto.VectorSearchResult
import org.springframework.stereotype.Service

@Service
class ProjectSearchService(private val service: ProjectService, private val access: ProjectAccess) {
    fun search(email: String, query: String, limit: Int): List<VectorSearchResult> {
        val actor = runCatching { access.actor(email) }.getOrNull() ?: return emptyList()
        val matches = service.list(email, query.take(200)).take(limit)
        return matches.filter { runCatching { access.require(it.id, actor) }.isSuccess }.map { project ->
            VectorSearchResult("project:${project.id}", 0.8f, mapOf("entityType" to "project", "title" to project.name,
                "text" to "${project.clientName} · ${project.category.name.lowercase()}", "source" to "Projects", "matchMode" to "Keyword match", "href" to "/projects/${project.id}"))
        }
    }
}
