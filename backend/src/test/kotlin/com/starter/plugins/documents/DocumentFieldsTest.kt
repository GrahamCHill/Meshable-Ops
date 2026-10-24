package com.starter.plugins.documents

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentFieldsTest {
    private val mapper = jacksonObjectMapper()
    private val fields = DocumentFields()
    private fun template(vararg keys: String) = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(mapOf("apiFields" to keys.map { mapOf("key" to it, "label" to it, "required" to false, "maxLength" to 100) }))
    private fun values(vararg pairs: Pair<String, String>) = pairs.associate { it.first to mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(it.second) }
    @Test fun `money tax and totals are computed server side with decimal rounding and ignore supplied totals`() {
        val template = template("subtotal", "rate", "tax", "total")
        val rules = listOf(DataFieldRule("subtotal", DataFieldType.MONEY), DataFieldRule("rate", DataFieldType.NUMBER),
            DataFieldRule("tax", DataFieldType.DERIVED, Calculation.PERCENT, listOf("subtotal", "rate")), DataFieldRule("total", DataFieldType.DERIVED, Calculation.ADD, listOf("subtotal", "tax")))
        val result = fields.resolve(template, rules, values("subtotal" to "12.34", "rate" to "15", "total" to "0"))
        assertThat(result).containsEntry("subtotal", "12.34").containsEntry("tax", "1.85").containsEntry("total", "14.19")
    }
    @Test fun `integer percentage calculations retain fractional result`() {
        val result = fields.resolve(template("a", "b", "c"), listOf(DataFieldRule("a", DataFieldType.NUMBER), DataFieldRule("b", DataFieldType.NUMBER), DataFieldRule("c", DataFieldType.DERIVED, Calculation.PERCENT, listOf("a", "b"))), values("a" to "12", "b" to "5"))
        assertThat(result["c"]).isEqualTo("0.60")
    }
    @Test fun `cycles unknown operands text operands divide by zero and huge numbers fail safely`() {
        val source = template("a", "b")
        val cycle = listOf(DataFieldRule("a", DataFieldType.DERIVED, Calculation.ADD, listOf("b", "b")), DataFieldRule("b", DataFieldType.DERIVED, Calculation.ADD, listOf("a", "a")))
        assertThatThrownBy { fields.validate(source, cycle) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("cycle")
        assertThatThrownBy { fields.validate(source, listOf(DataFieldRule("a", DataFieldType.DERIVED, Calculation.ADD, listOf("b", "absent")))) }.isInstanceOf(BadRequestException::class.java)
        val divide = listOf(DataFieldRule("a", DataFieldType.NUMBER), DataFieldRule("b", DataFieldType.DERIVED, Calculation.DIVIDE, listOf("a", "a")))
        assertThatThrownBy { fields.resolve(source, divide, values("a" to "0")) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("zero")
        assertThatThrownBy { fields.resolve(source, listOf(DataFieldRule("a", DataFieldType.NUMBER)), values("a" to "9".repeat(40))) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `required fields unknown keys prototype paths and nested values are rejected`() {
        val required = mapper.readTree("""{"apiFields":[{"key":"customer","label":"Customer","required":true,"maxLength":20}]}""")
        assertThatThrownBy { fields.resolve(required, emptyList(), emptyMap()) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("required")
        assertThatThrownBy { fields.resolve(required, emptyList(), values("unknown" to "value")) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { fields.validate(template("constructor.token"), emptyList()) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { fields.resolve(required, emptyList(), mapOf("customer" to mapper.readTree("{\"nested\":true}"))) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `typed dates email lengths and text control characters are validated`() {
        assertThatThrownBy { fields.resolve(template("date"), listOf(DataFieldRule("date", DataFieldType.DATE)), values("date" to "2026-02-31")) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { fields.resolve(template("email"), listOf(DataFieldRule("email", DataFieldType.EMAIL)), values("email" to "not-an-email")) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { fields.resolve(template("text"), emptyList(), values("text" to "a".repeat(101))) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { fields.resolve(template("text"), emptyList(), values("text" to "hidden\u0000control")) }.isInstanceOf(BadRequestException::class.java)
        assertThat(fields.resolve(template("text"), emptyList(), values("text" to "first\nsecond"))["text"]).isEqualTo("first\nsecond")
    }
}
