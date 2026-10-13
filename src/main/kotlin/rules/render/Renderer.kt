package rules.render

import rules.model.Expr
import rules.model.Value
import rules.ops.Notation
import rules.ops.OperatorRegistry
import rules.schema.Schema

/**
 * Renders expressions as deterministic human-readable text, e.g.
 * `if country = "Germany" then office in {"Munich", "Berlin"}`.
 *
 * Parentheses are inserted only where precedence requires them, so the text reads naturally
 * but still parses back to exactly one tree.
 */
class Renderer(
    private val label: (fieldName: String) -> String = { it },
    private val operators: OperatorRegistry = OperatorRegistry.Default,
) {
    constructor(schema: Schema, operators: OperatorRegistry = OperatorRegistry.Default) : this(schema::labelOf, operators)

    fun render(expr: Expr): String = node(expr).text

    private class Rendered(val text: String, val precedence: Int)

    private fun node(expr: Expr): Rendered = when (expr) {
        is Expr.Field -> Rendered(label(expr.name), ATOM)
        is Expr.Literal -> Rendered(literal(expr.value), ATOM)
        is Expr.Call -> call(expr)
    }

    private fun call(expr: Expr.Call): Rendered {
        val op = operators[expr.op]
        val args = expr.args.map(::node)
        if (op == null || args.size != op.signature.arity) {
            // Still show something meaningful for trees that would fail type checking.
            return Rendered(args.joinToString(prefix = "${expr.op}(", postfix = ")") { it.text }, ATOM)
        }
        val notation = op.notation
        val p = notation.precedence
        val text = when (notation) {
            is Notation.Infix -> {
                val left = args[0].wrap(if (notation.leftAssociative) p else p + 1)
                "$left ${notation.symbol} ${args[1].wrap(p + 1)}"
            }
            is Notation.Prefix -> "${notation.word} ${args[0].wrap(ATOM)}"
            is Notation.Postfix -> "${args[0].wrap(p + 1)} ${notation.word}"
            is Notation.Keywords -> notation.keywords.zip(args) { keyword, arg -> "$keyword ${arg.wrap(p + 1)}" }.joinToString(" ")
        }
        return Rendered(text, p)
    }

    private fun Rendered.wrap(minPrecedence: Int) = if (precedence >= minPrecedence) text else "($text)"

    private fun literal(value: Value): String = when (value) {
        is Value.Str -> quote(value.value)
        is Value.Num -> value.value.stripTrailingZeros().toPlainString()
        is Value.Bool -> value.value.toString()
        is Value.ListOf -> value.items.joinToString(prefix = "{", postfix = "}", transform = ::literal)
        Value.Missing -> "missing"
    }

    private fun quote(s: String) = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }

    private companion object {
        const val ATOM = Int.MAX_VALUE
    }
}
