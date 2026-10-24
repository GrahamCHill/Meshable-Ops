package com.starter.documents

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class DocumentAccessTest {
    @Test fun `template category is optional metadata without fixed business document types`() {
        jakarta.validation.Validation.buildDefaultValidatorFactory().use { factory ->
            val validator = factory.validator
            val request = TemplateRequest("General document", template = jacksonObjectMapper().createObjectNode())
            assertThat(validator.validate(request)).isEmpty()
            assertThat(validator.validate(request.copy(category = "Internal policy"))).isEmpty()
            assertThat(validator.validate(request.copy(category = "x".repeat(101))).map { it.propertyPath.toString() }).contains("category")
        }
    }
    @Test fun `users cannot access or attach another users document and administrators can manage it`() {
        val repository = mock(GeneratedDocumentRepository::class.java)
        val access = DocumentAccess(mock(UserRepository::class.java), repository)
        val owner = User(); val other = User(); val pdf = GeneratedDocument(ownerId = owner.id)
        `when`(repository.findById(pdf.id)).thenReturn(Optional.of(pdf))
        assertThat(access.document(owner, pdf.id)).isSameAs(pdf)
        assertThatThrownBy { access.document(other, pdf.id) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { access.references(other, listOf(pdf.id)) }.isInstanceOf(NotFoundException::class.java)
        assertThat(access.document(User(role = "ADMIN"), pdf.id)).isSameAs(pdf)
    }
    @Test fun `email passwords and persisted document source cannot appear in serialized metadata`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val message = EmailDocumentRequest("r@example.test", "Quote", "Attached", DocumentEmailProtection.PASSWORD, "private-share-password")
        assertThat(mapper.writeValueAsString(message)).doesNotContain("private-share-password", "password")
        assertThat(mapper.writeValueAsString(GeneratedDocument(pdfBytes = "private-pdf-bytes".toByteArray(), valuesJson = "private-field-values", templateSnapshot = "private-source")))
            .doesNotContain("private-pdf-bytes", "private-field-values", "private-source", "pdfBytes", "valuesJson", "templateSnapshot")
    }
}
