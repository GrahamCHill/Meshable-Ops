package com.starter.projects

import com.starter.exception.BadRequestException
import java.net.URI

object ProjectValidation {
    fun name(value: String, max: Int = 160) { if (value.isBlank() || value.length > max || value.any(Char::isISOControl)) invalid("Use a name of 1–$max characters") }
    fun contacts(values: List<Contact>) {
        if (values.size > 30 || values.map { it.id }.distinct().size != values.size) invalid("Use up to 30 unique contacts")
        values.forEach { contact ->
            name(contact.name)
            if (contact.email.length > 254 || contact.email.isNotBlank() && !contact.email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) ||
                contact.phone.length > 80 || contact.position.length > 160 || listOf(contact.phone, contact.position).any { it.any(Char::isISOControl) }) invalid("Use valid contact details")
        }
    }
    fun links(values: List<WebLink>) {
        if (values.size > 50) invalid("Use up to 50 web links")
        values.forEach { link ->
            name(link.label)
            val uri = runCatching { URI(link.url) }.getOrNull()
            if (link.url.length > 2000 || uri?.scheme !in setOf("https", "http") || uri?.host.isNullOrBlank() || uri?.userInfo != null || link.url.any(Char::isISOControl)) invalid("Use HTTP(S) links without embedded credentials")
        }
    }
    fun invalid(message: String): Nothing = throw BadRequestException(message)
}
