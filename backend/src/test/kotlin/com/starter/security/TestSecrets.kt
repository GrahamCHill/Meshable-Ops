package com.starter.security

import java.util.Base64

internal fun testEncryption(seed: Byte = 7) = SecretEncryption(Base64.getEncoder().encodeToString(ByteArray(32) { seed }))
