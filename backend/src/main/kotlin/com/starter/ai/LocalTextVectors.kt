package com.starter.ai

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/** Deterministic text matching needs neither model credentials nor another running service. */
object LocalTextVectors {
    const val MODEL = "local-feature-hash-v2"
    private val words = Regex("[\\p{L}\\p{N}]+")

    fun embed(text: String): List<Float> {
        val tokens = words.findAll(Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT))
            .map { it.value }.toList().ifEmpty { listOf("empty") }
        val features = (tokens.map { "word:$it" } + tokens.zipWithNext { a, b -> "pair:$a $b" }).groupingBy { it }.eachCount()
        val vector = DoubleArray(384)
        val digest = MessageDigest.getInstance("SHA-256")
        features.forEach { (feature, count) ->
            val hash = digest.digest(feature.toByteArray(Charsets.UTF_8))
            val index = (ByteBuffer.wrap(hash).int.toLong() and 0xffffffffL).rem(384).toInt()
            val sign = if ((hash[4].toInt() and 1) == 0) 1 else -1
            vector[index] += sign * (1.0 + ln(1.0 + count))
        }
        var norm = sqrt(vector.sumOf { it * it })
        if (norm == 0.0) { vector[0] = 1.0; norm = 1.0 }
        return vector.map { (it / norm).toFloat() }
    }
}
