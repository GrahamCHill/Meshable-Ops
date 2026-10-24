package com.starter.projects

import com.starter.exception.BadRequestException

object ProjectSectionRules {
    fun validate(settings: ProjectSettings) {
        val sections = settings.sections
        if (sections.size !in 1..20 || sections.map { it.id }.distinct().size != sections.size ||
            sections.any { !it.id.matches(Regex("[A-Za-z0-9_-]{1,40}")) } ||
            sections.map { it.name.trim().lowercase() }.distinct().size != sections.size ||
            sections.none { it.id == settings.defaultSectionId }) throw BadRequestException("Use 1–20 unique project sections and choose a default section")
        sections.forEach { ProjectValidation.name(it.name.trim(), 80) }
    }
    fun require(settings: ProjectSettings, id: String): String {
        if (settings.sections.none { it.id == id }) throw BadRequestException("Choose an existing project section")
        return id
    }
    fun name(settings: ProjectSettings, id: String) = settings.sections.find { it.id == id }?.name ?: id
}
