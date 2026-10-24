package com.starter.email

/** Internal-only attachments. Public email requests cannot upload arbitrary attachments. */
class EmailAttachment(val filename: String, val bytes: ByteArray) {
    override fun toString() = "EmailAttachment()"
    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
}
