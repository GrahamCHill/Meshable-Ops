package com.starter.service

import com.starter.exception.BadRequestException

/** Store provider-ready international numbers while accepting familiar display formatting. */
object ContactNumbers {
    fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (value.length > 32 || !value.matches(Regex("[0-9+() .-]+"))) invalid()
        val compact = value.replace(Regex("[() .-]"), "").let { if (it.startsWith("00")) "+" + it.drop(2) else it }
        if (!compact.matches(Regex("\\+?[1-9][0-9]{6,14}"))) invalid()
        return compact
    }
    private fun invalid(): Nothing = throw BadRequestException("Use an international contact number with its calling code, for example +44 20 7946 0123 or +27 82 123 4567")
}
