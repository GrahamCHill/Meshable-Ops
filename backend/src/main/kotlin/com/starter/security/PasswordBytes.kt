package com.starter.security

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PasswordBytesValidator::class])
annotation class PasswordBytes(
    val message: String = "Password must be at most 72 UTF-8 bytes",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PasswordBytesValidator : ConstraintValidator<PasswordBytes, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext) = value == null || value.toByteArray(Charsets.UTF_8).size <= 72
}
