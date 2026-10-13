package rules.ops

import rules.model.EvalError
import rules.model.Type
import rules.model.Value
import java.math.BigDecimal
import java.math.MathContext

/**
 * The built-in operator catalog. Each entry is the complete definition of an operation.
 * Ids are part of the persisted format and must never change once released.
 */
object Operators {
    // Precedence levels for rendering: higher binds tighter.
    private const val CONDITIONAL = 1
    private const val OR = 2
    private const val AND = 3
    private const val NOT = 4
    private const val COMPARISON = 5
    private const val ADDITIVE = 6
    private const val MULTIPLICATIVE = 7

    // --- Equality ---

    private val sameScalarType = Signature(2, "(T, T) -> boolean, T is string, number or boolean") { (a, b) ->
        Type.Bool.takeIf { a == b && a !is Type.ListOf }
    }

    private fun equality(id: String, symbol: String, expected: Boolean) =
        Operator(id, sameScalarType, Notation.Infix(symbol, COMPARISON, leftAssociative = false)) { args ->
            val left = args.value(0)
            val right = args.value(1)
            if (left.type != right.type) {
                args.fail(
                    EvalError.Code.TYPE_MISMATCH,
                    "cannot compare ${args.describe(0)} (${left.type}) with ${args.describe(1)} (${right.type})",
                )
            }
            Value.of((left == right) == expected)
        }

    val Eq = equality("eq", "=", expected = true)
    val Neq = equality("neq", "≠", expected = false)

    // --- Numeric comparison ---

    private fun ordering(id: String, symbol: String, test: (Int) -> Boolean) = Operator(
        id,
        Signature.fixed(Type.Num, Type.Num, result = Type.Bool),
        Notation.Infix(symbol, COMPARISON, leftAssociative = false),
    ) { args -> Value.of(test(args.number(0).compareTo(args.number(1)))) }

    val Lt = ordering("lt", "<") { it < 0 }
    val Le = ordering("le", "≤") { it <= 0 }
    val Gt = ordering("gt", ">") { it > 0 }
    val Ge = ordering("ge", "≥") { it >= 0 }

    // --- Membership ---

    val In = Operator(
        "in",
        Signature(2, "(T, list<T>) -> boolean") { (item, list) -> Type.Bool.takeIf { list == Type.ListOf(item) } },
        Notation.Infix("in", COMPARISON, leftAssociative = false),
    ) { args -> Value.of(args.value(0) in args.list(1)) }

    // --- Presence and blankness (the only operators that accept missing values) ---

    val IsBlank = Operator(
        "isBlank",
        Signature.fixed(Type.Str, result = Type.Bool),
        Notation.Postfix("is blank", COMPARISON),
    ) { args -> Value.of(isBlank(args)) }

    val IsNotBlank = Operator(
        "isNotBlank",
        Signature.fixed(Type.Str, result = Type.Bool),
        Notation.Postfix("is not blank", COMPARISON),
    ) { args -> Value.of(!isBlank(args)) }

    private fun isBlank(args: Arguments) = args.valueOrMissing(0) == Value.Missing || args.string(0).isBlank()

    val IsPresent = Operator(
        "isPresent",
        Signature(1, "(T) -> boolean") { Type.Bool },
        Notation.Postfix("is present", COMPARISON),
    ) { args -> Value.of(args.valueOrMissing(0) != Value.Missing) }

    // --- Boolean logic (short-circuiting) ---

    private val boolBinary = Signature.fixed(Type.Bool, Type.Bool, result = Type.Bool)

    val And = Operator("and", boolBinary, Notation.Infix("and", AND)) { args ->
        Value.of(args.boolean(0) && args.boolean(1))
    }

    val Or = Operator("or", boolBinary, Notation.Infix("or", OR)) { args ->
        Value.of(args.boolean(0) || args.boolean(1))
    }

    val Not = Operator("not", Signature.fixed(Type.Bool, result = Type.Bool), Notation.Prefix("not", NOT)) { args ->
        Value.of(!args.boolean(0))
    }

    /** `if a then b`: vacuously true when the condition does not hold. */
    val Implies = Operator("implies", boolBinary, Notation.Keywords(listOf("if", "then"), CONDITIONAL)) { args ->
        Value.of(!args.boolean(0) || args.boolean(1))
    }

    val IfElse = Operator(
        "if",
        Signature(3, "(boolean, T, T) -> T") { (condition, then, otherwise) ->
            then.takeIf { condition == Type.Bool && then == otherwise }
        },
        Notation.Keywords(listOf("if", "then", "else"), CONDITIONAL),
    ) { args -> if (args.boolean(0)) args.value(1) else args.value(2) }

    // --- Arithmetic ---

    private fun arithmetic(id: String, symbol: String, precedence: Int, compute: (BigDecimal, BigDecimal) -> BigDecimal) =
        Operator(id, Signature.fixed(Type.Num, Type.Num, result = Type.Num), Notation.Infix(symbol, precedence)) { args ->
            Value.Num(compute(args.number(0), args.number(1)))
        }

    val Add = arithmetic("add", "+", ADDITIVE, BigDecimal::add)
    val Sub = arithmetic("sub", "-", ADDITIVE, BigDecimal::subtract)
    val Mul = arithmetic("mul", "*", MULTIPLICATIVE, BigDecimal::multiply)

    val Div = Operator(
        "div",
        Signature.fixed(Type.Num, Type.Num, result = Type.Num),
        Notation.Infix("/", MULTIPLICATIVE),
    ) { args ->
        val dividend = args.number(0)
        val divisor = args.number(1)
        if (divisor.signum() == 0) {
            args.fail(EvalError.Code.DIVISION_BY_ZERO, "division by zero: ${args.describe(1)} is 0")
        }
        Value.Num(dividend.divide(divisor, MathContext.DECIMAL128))
    }

    val all: List<Operator> = listOf(
        Eq, Neq, Lt, Le, Gt, Ge, In,
        IsBlank, IsNotBlank, IsPresent,
        And, Or, Not, Implies, IfElse,
        Add, Sub, Mul, Div,
    )
}
