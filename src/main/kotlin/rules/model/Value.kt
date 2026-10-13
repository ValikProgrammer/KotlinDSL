package rules.model

import kotlinx.serialization.Serializable
import rules.serialization.ValueSerializer
import java.math.BigDecimal

/**
 * Runtime value produced by evaluation and stored in literals.
 *
 * Numbers are [BigDecimal] so that rules like `hours = 37.5` or `0.1 + 0.2 = 0.3` behave exactly,
 * and two numbers are equal when they are numerically equal (`40` == `40.0`).
 */
@Serializable(with = ValueSerializer::class)
sealed interface Value {
    /** `null` only for [Missing]: a missing value has no type. */
    val type: Type?

    data class Str(val value: String) : Value {
        override val type get() = Type.Str
    }

    class Num(val value: BigDecimal) : Value {
        constructor(value: Long) : this(BigDecimal.valueOf(value))

        override val type get() = Type.Num
        override fun equals(other: Any?) = other is Num && value.compareTo(other.value) == 0
        override fun hashCode() = value.stripTrailingZeros().hashCode()
        override fun toString() = "Num(${value.toPlainString()})"
    }

    data class Bool(val value: Boolean) : Value {
        override val type get() = Type.Bool
    }

    /** Homogeneous, non-empty list. Only appears as a literal (e.g. the allowed values of `in`). */
    data class ListOf(val items: List<Value>) : Value {
        override val type: Type? get() = items.firstOrNull()?.type?.let(Type::ListOf)
    }

    /** Absent or `null` field value. */
    data object Missing : Value {
        override val type: Type? get() = null
    }

    companion object {
        val TRUE = Bool(true)
        val FALSE = Bool(false)
        fun of(b: Boolean) = if (b) TRUE else FALSE
    }
}
