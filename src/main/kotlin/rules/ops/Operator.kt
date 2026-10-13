package rules.ops

import rules.model.EvalError
import rules.model.Type
import rules.model.Value
import java.math.BigDecimal

/**
 * Everything the platform knows about one operation, defined in a single place:
 * its stable [id] (used in files), its typing rule, its notation for rendering, and its semantics.
 *
 * Evaluator, renderer, type checker and serializer are generic over operators, so a new operation
 * is a new [Operator] value registered in an [OperatorRegistry] and nothing else.
 */
class Operator(
    val id: String,
    val signature: Signature,
    val notation: Notation,
    private val semantics: (Arguments) -> Value,
) {
    fun evaluate(args: Arguments): Value = semantics(args)

    override fun toString() = "Operator($id)"
}

/** Typing rule: given operand types, returns the result type or `null` if the operands are not accepted. */
class Signature(
    val arity: Int,
    /** Human-readable form used in error messages, e.g. `(number, number) -> boolean`. */
    val description: String,
    val resultType: (List<Type>) -> Type?,
) {
    companion object {
        fun fixed(vararg params: Type, result: Type) = Signature(
            arity = params.size,
            description = params.joinToString(prefix = "(", postfix = ") -> $result"),
        ) { args -> result.takeIf { args == params.toList() } }
    }
}

/** How a call is written in human-readable text. Higher [precedence] binds tighter. */
sealed interface Notation {
    val precedence: Int

    /** `a <symbol> b`. Non-associative operators get parentheses around same-precedence operands on both sides. */
    data class Infix(val symbol: String, override val precedence: Int, val leftAssociative: Boolean = true) : Notation

    /** `<word> (a)` */
    data class Prefix(val word: String, override val precedence: Int) : Notation

    /** `a <word>` */
    data class Postfix(val word: String, override val precedence: Int) : Notation

    /** Each keyword precedes one operand: `if a then b else c`. */
    data class Keywords(val keywords: List<String>, override val precedence: Int) : Notation
}

/**
 * Lazily evaluated operands of a call. Operands are evaluated only when requested,
 * which gives short-circuit semantics for `and`, `or`, `implies` and `if`.
 *
 * The typed accessors fail with [EvalError.Code.TYPE_MISMATCH] and the non-`OrMissing` accessors fail with
 * [EvalError.Code.MISSING_VALUE], so operator semantics can stay focused on the happy path.
 */
interface Arguments {
    val size: Int

    /** Evaluated operand; never [Value.Missing]. */
    fun value(index: Int): Value

    /** Evaluated operand; may be [Value.Missing]. */
    fun valueOrMissing(index: Int): Value

    fun fail(code: EvalError.Code, message: String): Nothing

    /** Rendered text of the operand, for error messages. */
    fun describe(index: Int): String

    fun string(index: Int): String = expect<Value.Str>(index, Type.Str).value
    fun number(index: Int): BigDecimal = expect<Value.Num>(index, Type.Num).value
    fun boolean(index: Int): Boolean = expect<Value.Bool>(index, Type.Bool).value
    fun list(index: Int): List<Value> = expect<Value.ListOf>(index, null).items
}

private inline fun <reified V : Value> Arguments.expect(index: Int, expected: Type?): V {
    val value = value(index)
    return value as? V ?: fail(
        EvalError.Code.TYPE_MISMATCH,
        "${describe(index)} is ${value.type}, expected ${expected ?: "a list"}",
    )
}
