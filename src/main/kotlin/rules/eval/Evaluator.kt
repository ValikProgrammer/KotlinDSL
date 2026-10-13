package rules.eval

import rules.model.EvalError
import rules.model.EvaluationException
import rules.model.Expr
import rules.model.Rule
import rules.model.RuleSet
import rules.model.Value
import rules.ops.Arguments
import rules.ops.OperatorRegistry
import rules.render.Renderer
import rules.schema.Record
import rules.schema.Schema

sealed interface RuleResult {
    data object Passed : RuleResult
    data object Violated : RuleResult

    /** The rule could not be checked because a required field is missing; see that field's "is required" rule. */
    data class Skipped(val reason: String) : RuleResult
    data class Error(val error: EvalError) : RuleResult
}

/**
 * Evaluates expression trees against records. Knows nothing about individual operations:
 * it resolves fields through the [schema] and delegates every call to its [rules.ops.Operator].
 */
class Evaluator(
    private val schema: Schema,
    private val operators: OperatorRegistry = OperatorRegistry.Default,
    private val renderer: Renderer = Renderer(schema, operators),
) {
    /** Never throws: problems with the data or the rule are reported as [RuleResult.Error]. */
    fun evaluate(rule: Rule, record: Record): RuleResult = try {
        when (val result = evaluate(rule.expression, record)) {
            Value.TRUE -> RuleResult.Passed
            Value.FALSE -> RuleResult.Violated
            else -> RuleResult.Error(
                EvalError(EvalError.Code.TYPE_MISMATCH, "rule '${rule.id}' produced ${describe(result)}, expected boolean"),
            )
        }
    } catch (e: EvaluationException) {
        if (e.error.code == EvalError.Code.REQUIRED_FIELD_MISSING) RuleResult.Skipped(e.error.message)
        else RuleResult.Error(e.error)
    }

    /** Checks the schema's required fields first, then every rule of [ruleSet]. */
    fun evaluateAll(ruleSet: RuleSet, record: Record): List<Pair<Rule, RuleResult>> =
        (schema.requiredFieldRules() + ruleSet.rules).map { it to evaluate(it, record) }

    /** Evaluates any expression. Throws [EvaluationException] on errors. */
    fun evaluate(expr: Expr, record: Record): Value = when (expr) {
        is Expr.Literal -> expr.value
        is Expr.Field -> {
            val field = schema.fieldDef(expr.name)
                ?: fail(EvalError.Code.UNKNOWN_FIELD, "unknown field '${expr.name}' in schema '${schema.schemaId}'")
            field.read(record[expr.name])
        }
        is Expr.Call -> {
            val op = operators[expr.op] ?: fail(EvalError.Code.UNKNOWN_OPERATOR, "unknown operator '${expr.op}'")
            if (expr.args.size != op.signature.arity) {
                fail(EvalError.Code.WRONG_ARITY, "'${op.id}' expects ${op.signature.arity} operands, got ${expr.args.size}")
            }
            op.evaluate(LazyArguments(expr, record))
        }
    }

    private fun describe(value: Value) = if (value == Value.Missing) "a missing value" else "${value.type}"

    private inner class LazyArguments(private val call: Expr.Call, private val record: Record) : Arguments {
        private val cache = arrayOfNulls<Value>(call.args.size)

        override val size get() = call.args.size

        override fun valueOrMissing(index: Int): Value =
            cache[index] ?: evaluate(call.args[index], record).also { cache[index] = it }

        override fun value(index: Int): Value {
            val value = valueOrMissing(index)
            if (value == Value.Missing) {
                val arg = call.args[index]
                if (arg is Expr.Field && schema.fieldDef(arg.name)?.required == true) {
                    this@Evaluator.fail(EvalError.Code.REQUIRED_FIELD_MISSING, "required field '${arg.name}' is missing")
                }
                fail(EvalError.Code.MISSING_VALUE, "${describe(index)} is missing")
            }
            return value
        }

        override fun describe(index: Int) = renderer.render(call.args[index])

        override fun fail(code: EvalError.Code, message: String): Nothing =
            this@Evaluator.fail(code, "$message (in: ${renderer.render(call)})")
    }

    private fun fail(code: EvalError.Code, message: String): Nothing = throw EvaluationException(EvalError(code, message))
}
