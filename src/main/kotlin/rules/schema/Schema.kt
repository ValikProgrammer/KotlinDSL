package rules.schema

import rules.dsl.Ex
import rules.model.EvalError
import rules.model.EvaluationException
import rules.model.Expr
import rules.model.Rule
import rules.model.Type
import rules.model.Value
import java.math.BigDecimal
import java.math.BigInteger

/** A record is a raw field-name-to-value map, e.g. a row from an HR system. Values are not trusted. */
typealias Record = Map<String, Any?>

/**
 * @property required whether every record must have a value for this field. Nullability cannot be inferred
 *   from data; it is declared here from what the source system guarantees.
 */
class FieldDef(val name: String, val type: Type, val label: String, val required: Boolean) {
    /**
     * Converts a raw record value into a [Value] of the declared [type].
     * There is no implicit coercion: `"40"` is not a number, it is a type mismatch.
     */
    fun read(raw: Any?): Value {
        if (raw == null) return Value.Missing
        val value = when (type) {
            Type.Str -> (raw as? String)?.let(Value::Str)
            Type.Bool -> (raw as? Boolean)?.let(Value::Bool)
            Type.Num -> toBigDecimal(raw)?.let(Value::Num)
            is Type.ListOf -> null
        }
        return value ?: throw EvaluationException(
            EvalError(
                EvalError.Code.TYPE_MISMATCH,
                "field '$name' should be $type but the record contains ${raw::class.simpleName} ${render(raw)}",
            ),
        )
    }

    private fun toBigDecimal(raw: Any): BigDecimal? = when (raw) {
        is BigDecimal -> raw
        is BigInteger -> raw.toBigDecimal()
        is Int, is Long, is Short, is Byte -> BigDecimal.valueOf((raw as Number).toLong())
        is Double, is Float -> (raw as Number).toDouble().takeIf { it.isFinite() }?.let { BigDecimal(it.toString()) }
        else -> null
    }

    private fun render(raw: Any) = if (raw is String) "\"$raw\"" else raw.toString()
}

/**
 * Describes the fields rules can refer to. Members are named `schemaId`, `fieldDef`, ... so they do not clash
 * with typical field names like `name` or `id` declared by subclasses.
 *
 * Subclasses declare typed fields that double as DSL entry points:
 *
 * ```
 * object EmployeeSchema : Schema("employee") {
 *     val name = string("name")
 * }
 * ```
 */
abstract class Schema(val schemaId: String) {
    private val definitions = linkedMapOf<String, FieldDef>()

    val fieldDefs: Collection<FieldDef> get() = definitions.values

    fun fieldDef(name: String): FieldDef? = definitions[name]

    fun labelOf(name: String): String = definitions[name]?.label ?: name

    // Required fields are non-null in the DSL. Optional fields are nullable (`Ex<String?>`), so a rule cannot
    // compare them until it decides what a missing value means (see `ifPresent` and `presentAnd`).

    protected fun string(name: String, label: String = humanize(name)): Ex<String> = declare(name, Type.Str, label, true)
    protected fun number(name: String, label: String = humanize(name)): Ex<BigDecimal> = declare(name, Type.Num, label, true)
    protected fun boolean(name: String, label: String = humanize(name)): Ex<Boolean> = declare(name, Type.Bool, label, true)

    protected fun optionalString(name: String, label: String = humanize(name)): Ex<String?> =
        declare(name, Type.Str, label, false)
    protected fun optionalNumber(name: String, label: String = humanize(name)): Ex<BigDecimal?> =
        declare(name, Type.Num, label, false)
    protected fun optionalBoolean(name: String, label: String = humanize(name)): Ex<Boolean?> =
        declare(name, Type.Bool, label, false)

    /**
     * One rule per required field: "<field> is required". They are derived from the schema rather than stored
     * in rule files, so a missing value is reported once, with its real cause, instead of as a misleading
     * violation of every rule that reads the field.
     */
    fun requiredFieldRules(): List<Rule> = fieldDefs.filter { it.required }.map { field ->
        Rule(
            id = "${field.name}-required",
            description = "${field.label.replaceFirstChar(Char::uppercase)} is required",
            expression = Expr.Call("isPresent", Expr.Field(field.name)),
        )
    }

    private fun <T> declare(name: String, type: Type, label: String, required: Boolean): Ex<T> {
        val field = FieldDef(name, type, label, required)
        require(definitions.put(name, field) == null) { "Duplicate field '$name' in schema '$schemaId'" }
        return Ex(Expr.Field(name))
    }

    private fun humanize(name: String) = name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ").lowercase()
}
