package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import java.math.BigDecimal
import java.math.RoundingMode

/** A bounded interpreter for a documented Python-shaped language. Never invokes Python, eval, or reflection. */
object WorkflowCode {
    private fun bad(message: String): Nothing = throw BadRequestException("Workflow code: $message")
    private class Environment(val context: Map<String, String>) {
        val variables = mutableMapOf<String, Any?>()
        var operations = 0
        fun tick() { if (++operations > 2000) bad("operation limit exceeded") }
    }
    private fun interface Expression { fun value(env: Environment): Any? }
    private sealed interface Statement
    private data class Assign(val name: String, val expression: Expression) : Statement
    private data class Return(val expression: Expression) : Statement
    private data class If(val condition: Expression, val yes: List<Statement>, val no: List<Statement>) : Statement
    private data class Line(val indent: Int, val text: String)

    fun validate(source: String) { compile(source) }

    fun execute(source: String, context: Map<String, String>): Map<String, String> {
        val statements = compile(source)
        val env = Environment(context)
        fun run(block: List<Statement>): Map<*, *>? {
            for (statement in block) {
                env.tick()
                when (statement) {
                    is Assign -> env.variables[statement.name] = statement.expression.value(env)
                    is Return -> return statement.expression.value(env) as? Map<*, *> ?: bad("return a dictionary of named values")
                    is If -> run(if (truth(statement.condition.value(env))) statement.yes else statement.no)?.let { return it }
                }
            }
            return null
        }
        val result = run(statements) ?: bad("run(context) must return a dictionary on the chosen path")
        if (result.size > 40) bad("return at most 40 values")
        return result.entries.associate { (key, value) ->
            val name = key as? String ?: bad("output keys must be strings")
            if (!name.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,119}")) || (name.startsWith("trigger.") || name.startsWith("webhook.")) ||
                name.split('.').any { it in setOf("__proto__", "prototype", "constructor") }) bad("invalid or protected output field")
            val text = text(value)
            if (text.length > 500 || value is Map<*, *>) bad("output values must be scalar values of at most 500 characters")
            name to text
        }
    }

    private fun compile(source: String): List<Statement> {
        if (source.length > 8192 || '\t' in source) bad("use at most 8192 characters and four-space indentation")
        val lines = source.lines().map { it.trimEnd() }.filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
            .map { Line(it.length - it.trimStart().length, it.trimStart()) }
        if (lines.size !in 2..65 || lines.first() != Line(0, "def run(context):")) bad("begin with def run(context): and use at most 64 statements")
        var position = 1
        var returns = 0
        fun block(indent: Int): List<Statement> {
            if (indent > 20) bad("use at most five indentation levels")
            val statements = mutableListOf<Statement>()
            while (position < lines.size && lines[position].indent >= indent) {
                val line = lines[position]
                if (line.indent != indent) bad("indent nested blocks with four spaces")
                if (line.text == "else:") break
                position++
                when {
                    line.text.startsWith("if ") && line.text.endsWith(':') -> {
                        val condition = Parser(line.text.substring(3).dropLast(1)).parse()
                        val yes = block(indent + 4)
                        if (yes.isEmpty()) bad("if needs an indented body")
                        val no = if (position < lines.size && lines[position] == Line(indent, "else:")) { position++; block(indent + 4) } else emptyList()
                        statements += If(condition, yes, no)
                    }
                    line.text.startsWith("return ") -> { returns++; statements += Return(Parser(line.text.substring(7)).parse()) }
                    else -> {
                        val assignment = Regex("([a-zA-Z][a-zA-Z0-9_]{0,39})\\s*=\\s*(.+)").matchEntire(line.text) ?: bad("only assignments, if/else, and return are supported")
                        val name = assignment.groupValues[1]
                        if (name in setOf("context", "run", "True", "False", "None", "import", "exec", "eval")) bad("reserved variable name")
                        statements += Assign(name, Parser(assignment.groupValues[2]).parse())
                    }
                }
            }
            return statements
        }
        val statements = block(4)
        if (position != lines.size || returns == 0) bad("invalid indentation or missing return")
        return statements
    }

    private class Parser(source: String) {
        private val tokens = mutableListOf<String>()
        private var position = 0
        init {
            val pattern = Regex("\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|[0-9]+(?:\\.[0-9]+)?|[A-Za-z_][A-Za-z0-9_]*|==|!=|>=|<=|[{}\\[\\]():,+*/<>\\-=])")
            var offset = 0
            while (offset < source.trimEnd().length) {
                val match = pattern.find(source, offset)?.takeIf { it.range.first == offset } ?: bad("unsupported syntax; imports, attributes, loops, and library calls are unavailable")
                tokens += match.groupValues[1]; offset = match.range.last + 1
                if (tokens.size > 512) bad("expression is too large")
            }
        }
        fun parse(): Expression = expression().also { if (position != tokens.size) bad("unexpected token") }
        private fun peek() = tokens.getOrNull(position)
        private fun take() = tokens.getOrNull(position++) ?: bad("incomplete expression")
        private fun expect(token: String) { if (take() != token) bad("expected $token") }
        private fun expression(): Expression = binary(0)
        private val operators = listOf(setOf("or"), setOf("and"), setOf("==", "!=", ">", "<", ">=", "<="), setOf("+", "-"), setOf("*", "/"))
        private fun binary(level: Int): Expression {
            if (level == operators.size) return atom()
            var left = binary(level + 1)
            while (peek() in operators[level]) {
                val op = take(); val before = left; val right = binary(level + 1)
                left = Expression { env ->
                    env.tick()
                    val a = before.value(env)
                    when (op) {
                        "and" -> truth(a) && truth(right.value(env))
                        "or" -> truth(a) || truth(right.value(env))
                        else -> calculate(a, op, right.value(env))
                    }
                }
            }
            return left
        }
        private var depth = 0
        private fun atom(): Expression {
            if (++depth > 30) bad("expression nesting limit exceeded")
            val token = take()
            val result: Expression = when {
                token == "not" -> { val value = atom(); Expression { !truth(value.value(it)) } }
                token == "-" -> { val value = atom(); Expression { number(value.value(it)).negate() } }
                token == "(" -> expression().also { expect(")") }
                token == "{" -> {
                    val entries = mutableMapOf<String, Expression>()
                    if (peek() != "}") do {
                        val key = string(take()); expect(":")
                        if (key in entries) bad("duplicate output key")
                        entries[key] = expression()
                        if (peek() != ",") break
                        take()
                    } while (peek() != "}")
                    expect("}")
                    Expression { env -> entries.mapValues { it.value.value(env) } }
                }
                token.startsWith('"') || token.startsWith('\'') -> { val value = string(token); Expression { value } }
                token.firstOrNull()?.isDigit() == true -> { val value = number(token); Expression { value } }
                token == "True" -> Expression { true }
                token == "False" -> Expression { false }
                token == "None" -> Expression { null }
                token == "context" -> {
                    expect("["); val key = string(take()); expect("]")
                    Expression { it.tick(); it.context[key].orEmpty() }
                }
                peek() == "(" -> {
                    if (token !in setOf("number", "text", "len", "min", "max")) bad("unsupported function")
                    take(); val args = mutableListOf<Expression>()
                    if (peek() != ")") do { args += expression(); if (peek() != ",") break; take() } while (true)
                    expect(")")
                    if (args.size != if (token in setOf("min", "max")) 2 else 1) bad("incorrect function arguments")
                    Expression { env ->
                        env.tick(); val values = args.map { it.value(env) }
                        when (token) {
                            "number" -> number(values[0]); "text" -> text(values[0]); "len" -> BigDecimal(text(values[0]).length)
                            "min" -> number(values[0]).min(number(values[1])); else -> number(values[0]).max(number(values[1]))
                        }
                    }
                }
                token.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,39}")) -> Expression { env -> env.tick(); if (!env.variables.containsKey(token)) bad("unknown variable $token"); env.variables[token] }
                else -> bad("unsupported expression")
            }
            depth--
            return result
        }
    }
    private fun string(token: String): String {
        if (token.length < 2 || token.first() !in "\"'" || token.last() != token.first()) bad("use a quoted string")
        val output = StringBuilder(); var i = 1
        while (i < token.length - 1) {
            val c = token[i++]
            if (c != '\\') output.append(c) else {
                if (i >= token.length - 1) bad("invalid string escape")
                output.append(when (val escaped = token[i++]) { 'n' -> '\n'; 't' -> '\t'; '\\', '\'', '"' -> escaped; else -> bad("unsupported string escape") })
            }
        }
        return output.toString()
    }
    private fun number(value: Any?): BigDecimal {
        if (value is BigDecimal) return value
        val text = value?.toString().orEmpty()
        if (!text.matches(Regex("[+-]?[0-9]{1,30}(\\.[0-9]{1,12})?"))) bad("number() needs a bounded decimal")
        return text.toBigDecimal()
    }
    private fun text(value: Any?) = when (value) { null -> ""; true -> "True"; false -> "False"; is BigDecimal -> value.stripTrailingZeros().toPlainString(); else -> value.toString() }
    private fun truth(value: Any?) = when (value) { null -> false; is Boolean -> value; is BigDecimal -> value.signum() != 0; is String -> value.isNotEmpty(); else -> bad("use a scalar condition") }
    private fun calculate(a: Any?, op: String, b: Any?): Any {
        if (op == "==" || op == "!=") {
            val equal = if (a is BigDecimal && b is BigDecimal) a.compareTo(b) == 0 else a == b
            return if (op == "==") equal else !equal
        }
        if (op in setOf("<", ">", "<=", ">=")) {
            val comparison = if (a is BigDecimal && b is BigDecimal) a.compareTo(b) else if (a is String && b is String) a.compareTo(b) else bad("compare values of the same type")
            return when (op) { "<" -> comparison < 0; ">" -> comparison > 0; "<=" -> comparison <= 0; else -> comparison >= 0 }
        }
        if (op == "+" && a is String && b is String) return (a + b).also { if (it.length > 4096) bad("string limit exceeded") }
        val left = number(a); val right = number(b)
        val result = when (op) { "+" -> left + right; "-" -> left - right; "*" -> left * right; "/" -> { if (right.signum() == 0) bad("division by zero"); left.divide(right, 12, RoundingMode.HALF_UP) }; else -> bad("unsupported operator") }
        if (result.precision() > 60 || result.scale() > 24) bad("arithmetic limit exceeded")
        return result
    }
}
