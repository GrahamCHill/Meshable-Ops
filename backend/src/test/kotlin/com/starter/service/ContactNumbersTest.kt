package com.starter.service

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class ContactNumbersTest {
    @Test fun `calling codes and display formatting become delivery-ready numbers`() {
        assertThat(ContactNumbers.normalize("+44 (20) 7946-0123")).isEqualTo("+442079460123")
        assertThat(ContactNumbers.normalize("+27 82 123 4567")).isEqualTo("+27821234567")
        assertThat(ContactNumbers.normalize("0044 20 7946 0123")).isEqualTo("+442079460123")
        assertThat(ContactNumbers.normalize("27821234567")).isEqualTo("27821234567")
        assertThat(ContactNumbers.normalize(" ")).isNull()
    }
    @Test fun `ambiguous local numbers malformed codes and excessive input are rejected`() {
        listOf("0821234567", "+0 12 345678", "++27821234567", "+27\n821234567", "+27 abc", "+1234567890123456").forEach { value ->
            assertThatThrownBy { ContactNumbers.normalize(value) }.isInstanceOf(BadRequestException::class.java)
        }
    }
}
