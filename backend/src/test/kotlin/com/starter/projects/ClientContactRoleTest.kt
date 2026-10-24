package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientContactRoleTest {
    private val mapper = jacksonObjectMapper()
    @Test fun `contact relationship roles remain separate from job titles and legacy data is readable`() {
        val old = mapper.readValue("""{"name":"Client person","position":"Finance director"}""", Contact::class.java)
        assertThat(old.position).isEqualTo("Finance director")
        assertThat(old.role).isEmpty()
        val contact = old.copy(role = "Billing approver", phone = "+44 20 1234 5678")
        ProjectValidation.contacts(listOf(contact))
        assertThat(mapper.readValue(mapper.writeValueAsString(contact), Contact::class.java)).isEqualTo(contact)
    }
    @Test fun `contact roles reject oversized and control-character values`() {
        listOf("x".repeat(161), "Billing\nAdmin").forEach { role ->
            assertThatThrownBy { ProjectValidation.contacts(listOf(Contact(name = "Person", role = role))) }.isInstanceOf(BadRequestException::class.java)
        }
    }
}
