package com.starter.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Component
@Converter
class EncryptedSecretConverter(private val encryption: SecretEncryption) : AttributeConverter<String?, String?> {
    override fun convertToDatabaseColumn(attribute: String?) = encryption.encrypt(attribute)
    override fun convertToEntityAttribute(dbData: String?) = encryption.decrypt(dbData)
}
