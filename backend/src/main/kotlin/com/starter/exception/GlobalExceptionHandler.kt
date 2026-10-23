package com.starter.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import com.starter.ai.AiUnavailableException

private val log = LoggerFactory.getLogger("GlobalExceptionHandler")

data class ErrorResponse(val message: String, val status: Int, val timestamp: Instant = Instant.now())

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(com.starter.email.EmailDeliveryException::class)
    fun handleEmailUnavailable(ex: com.starter.email.EmailDeliveryException) =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse(ex.message ?: "Email delivery unavailable", 503))

    @ExceptionHandler(AiUnavailableException::class)
    fun handleAiUnavailable(ex: AiUnavailableException) =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse(ex.message ?: "AI service unavailable", 503))

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Not found", 404))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "Conflict", 409))

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(ex.message ?: "Bad request", 400))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(ex.message ?: "Forbidden", 403))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("Forbidden", 403))

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("Invalid credentials", 401))

    @ExceptionHandler(DisabledException::class)
    fun handleDisabled(ex: DisabledException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("This account has been deactivated", 401))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val errors = ex.bindingResult.fieldErrors.associate { fe: FieldError ->
            fe.field to (fe.defaultMessage ?: "Invalid")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("An unexpected error occurred", 500))
    }
}
