package rules.eval

import rules.model.Expr
import rules.model.Type
import rules.model.Value
import rules.ops.OperatorRegistry
import rules.render.Renderer
import rules.schema.Schema

/**
 * Static check of an expression against a schema, without any record.
 * The DSL already guarantees well-typed trees; this protects against trees from other sources
 * (files edited by hand, a future UI editor, older format versions).
 */
class TypeChecker(
    private val schema: Schema,
    private val operators: OperatorRegistry = OperatorRegistry.Default,
    private val renderer: Renderer = Renderer(schema, operators),
) {
    /** Returns all problems found, or an empty list if [expr] is well-typed and has type [expected]. */
    fun check(expr: Expr, expected: Type = Type.Bool): List<String> {
        val problems = mutableListOf<String>()
        val actual = infer(expr, problems)
        if (actual != null && actual != expected) {
            problems += "expression has type $actual, expected $expected: ${renderer.render(expr)}"
        }
        return problems
    }

    /** Infers the type of [expr]; returns `null` (and records a problem) if it cannot be typed. */
    private fun infer(expr: Expr, problems: MutableList<String>): Type? = when (expr) {
        is Expr.Field -> schema.fieldDef(expr.name)?.type
            ?: null.also { problems += "unknown field '${expr.name}'" }

        is Expr.Literal -> literalType(expr.value)
            ?: null.also { problems += "literal ${renderer.render(expr)} must be a non-empty list of one type or a scalar" }

        is Expr.Call -> {
            val argTypes = expr.args.map { infer(it, problems) }
            val op = operators[expr.op]
            when {
                op == null -> null.also { problems += "unknown operator '${expr.op}'" }
                argTypes.size != op.signature.arity ->
                    null.also { problems += "'${op.id}' expects ${op.signature.arity} operands, got ${argTypes.size}" }
                // An operand already failed: don't report a follow-up error for this call.
                argTypes.any { it == null } -> null
                else -> op.signature.resultType(argTypes.requireNoNulls()) ?: null.also {
                    problems += "'${op.id}' expects ${op.signature.description} but got " +
                        "${argTypes.joinToString(prefix = "(", postfix = ")")}: ${renderer.render(expr)}"
                }
            }
        }
    }

    private fun literalType(value: Value): Type? = when (value) {
        is Value.ListOf -> {
            val types = value.items.map(::literalType).toSet()
            types.singleOrNull()?.takeIf { it !is Type.ListOf }?.let(Type::ListOf)
        }
        Value.Missing -> null
        else -> value.type
    }
}
