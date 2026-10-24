package com.starter.plugins

import java.math.BigInteger

/** Bounded SemVer precedence, exact releases, inclusive hyphen ranges, and ANDed comparators. */
internal object PluginVersionRules {
    private val versionPattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?")
    private val hyphen = Regex("([^\\s]+)\\s+-\\s+([^\\s]+)")
    private val comparator = Regex("(>=|<=|>|<|=)\\s*([0-9A-Za-z.+-]+)")
    data class Version(val core: List<BigInteger>, val pre: List<String>) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            core.zip(other.core).forEach { (a,b) -> a.compareTo(b).takeIf { it != 0 }?.let { return it } }
            if (pre.isEmpty() || other.pre.isEmpty()) return when { pre.isEmpty() && other.pre.isEmpty() -> 0; pre.isEmpty() -> 1; else -> -1 }
            pre.zip(other.pre).forEach { (a,b) ->
                val an=a.toBigIntegerOrNull(); val bn=b.toBigIntegerOrNull()
                val order=when { an!=null && bn!=null -> an.compareTo(bn); an!=null -> -1; bn!=null -> 1; else -> a.compareTo(b) }
                if (order != 0) return order
            }
            return pre.size.compareTo(other.pre.size)
        }
    }
    fun version(value: String): Version? {
        if (value.length > 100) return null
        val match=versionPattern.matchEntire(value) ?: return null
        val pre=match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
        if (pre.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
        return Version((1..3).map { match.groupValues[it].toBigInteger() },pre)
    }
    fun rule(value: String): ((Version) -> Boolean)? {
        if (value.isBlank() || value.length > 200 || value.any(Char::isISOControl)) return null
        val input=value.trim()
        version(input)?.let { target -> return { it.compareTo(target)==0 } }
        hyphen.matchEntire(input)?.let { range ->
            val lower=version(range.groupValues[1]) ?: return null
            val upper=version(range.groupValues[2]) ?: return null
            if (lower > upper) return null
            return { it >= lower && it <= upper }
        }
        val parts=comparator.findAll(input).toList()
        if (parts.isEmpty() || parts.size > 4) return null
        var end=0
        val checks=parts.map { part ->
            if (input.substring(end,part.range.first).isNotBlank() || (end>0 && part.range.first==end)) return null
            end=part.range.last+1
            val target=version(part.groupValues[2]) ?: return null
            val operator=part.groupValues[1]
            val check: (Version) -> Boolean = { current -> val order=current.compareTo(target); when(operator) { ">=" -> order>=0; "<=" -> order<=0; ">" -> order>0; "<" -> order<0; else -> order==0 } }
            check
        }
        if (input.substring(end).isNotBlank()) return null
        return { current -> checks.all { it(current) } }
    }
}
