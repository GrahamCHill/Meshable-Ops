package com.starter.documents

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Declarative arithmetic only: no evaluation, scripts, URLs, or server variable interpolation. */
@Component
class DocumentFields {
    fun validate(template: JsonNode, rules: List<DataFieldRule>) {
        if (!template.isObject || template.path("apiFields").let { !it.isMissingNode && !it.isArray }) bad("Invalid template data fields")
        val fields = template.path("apiFields").toList()
        val keys = fields.map { it.path("key").asText() }
        if (keys.size > 50 || keys.distinct().size != keys.size || keys.any { !validKey(it) }) bad("Use up to 50 unique named data fields")
        if (rules.map { it.key }.distinct().size != rules.size || rules.any { it.key !in keys }) bad("Field rules must refer to unique template fields")
        val indexed = rules.associateBy { it.key }
        rules.forEach { rule ->
            if (rule.type == DataFieldType.DERIVED) {
                if (rule.operation == null || rule.inputs.size != 2 || rule.inputs.any { it !in keys || indexed[it]?.type !in setOf(DataFieldType.NUMBER, DataFieldType.MONEY, DataFieldType.DERIVED) }) bad("Calculations need two numeric or derived template fields")
            } else if (rule.operation != null || rule.inputs.isNotEmpty()) bad("Only derived fields can have a calculation")
        }
        val checked = mutableSetOf<String>()
        fun visit(key: String, path: Set<String>) {
            if (key in path) bad("Derived fields cannot contain a calculation cycle")
            if (key in checked) return
            indexed[key]?.inputs?.forEach { visit(it, path + key) }
            checked += key
        }
        keys.forEach { visit(it, emptySet()) }
    }

    fun resolve(template: JsonNode, rules: List<DataFieldRule>, values: Map<String, JsonNode>): Map<String, String> {
        validate(template, rules)
        val definitions = template.path("apiFields").associateBy { it.path("key").asText() }
        if (values.size > 50 || values.keys.any { it !in definitions || !validKey(it) }) bad("Supply only the template's named data fields")
        val indexed = rules.associateBy { it.key }; val output = linkedMapOf<String, String>()
        fun resolve(key: String): String {
            output[key]?.let { return it }
            val field = definitions.getValue(key); val label = field.path("label").asText(key).take(80)
            val rule = indexed[key] ?: DataFieldRule(key)
            val input = values[key]
            if (input != null && !input.isValueNode) bad("$label must be a single value")
            var value = input?.takeUnless { it.isNull }?.asText()?.trim().orEmpty().ifEmpty { field.path("fallback").asText("") }
            if (rule.type == DataFieldType.DERIVED) {
                val a = decimal(resolve(rule.inputs[0]), label); val b = decimal(resolve(rule.inputs[1]), label)
                value = when (rule.operation!!) {
                    Calculation.ADD -> a + b; Calculation.SUBTRACT -> a - b; Calculation.MULTIPLY -> a * b
                    Calculation.DIVIDE -> { if (b.compareTo(BigDecimal.ZERO) == 0) bad("$label cannot divide by zero"); a.divide(b, 8, RoundingMode.HALF_UP) }
                    Calculation.PERCENT -> (a * b).divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
                }.setScale(2, RoundingMode.HALF_UP).toPlainString()
                if (value.length > 30) bad("$label is outside the supported numeric range")
            } else if (value.isNotEmpty()) {
                value = when (rule.type) {
                    DataFieldType.NUMBER -> decimal(value, label).stripTrailingZeros().toPlainString()
                    DataFieldType.MONEY -> decimal(value, label).setScale(2, RoundingMode.HALF_UP).toPlainString()
                    DataFieldType.DATE -> runCatching { LocalDate.parse(value).toString() }.getOrElse { bad("$label must be an ISO date") }
                    DataFieldType.EMAIL -> { if (!Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(value) || value.length > 254) bad("$label must be an email address"); value }
                    else -> value
                }
            }
            if (field.path("required").asBoolean(false) && value.isBlank()) bad("$label is required")
            val max = field.path("maxLength").asInt(4000).coerceIn(1, 4000)
            if (value.length > max || value.any { it.isISOControl() && it !in "\r\n\t" }) bad("$label exceeds its text limit or contains control characters")
            output[key] = value; return value
        }
        definitions.keys.forEach { resolve(it) }
        return output
    }
    private fun decimal(value: String, label: String): BigDecimal {
        if (!Regex("-?[0-9]{1,15}(\\.[0-9]{1,6})?").matches(value)) bad("$label requires a number with at most 15 whole and six decimal digits")
        return BigDecimal(value)
    }
    private fun validKey(key: String) = Regex("[A-Za-z][A-Za-z0-9_.-]{0,119}").matches(key) && key.split('.').none { it in setOf("__proto__", "prototype", "constructor") }
    private fun bad(message: String): Nothing = throw BadRequestException(message)
}
