package rules.dsl

import rules.model.Expr
import rules.model.Value
import rules.ops.Operator
import rules.ops.Operators
import java.math.BigDecimal

private fun <R> call(op: Operator, vararg args: Ex<*>): Ex<R> = Ex(Expr.Call(op.id, args.map { it.node }))

// --- Literals ---

fun lit(value: String): Ex<String> = Ex(Expr.Literal(Value.Str(value)))
fun lit(value: Boolean): Ex<Boolean> = Ex(Expr.Literal(Value.of(value)))
fun lit(value: Number): Ex<BigDecimal> = Ex(Expr.Literal(Value.Num(value.toBigDecimalExact())))

private fun Number.toBigDecimalExact(): BigDecimal = when (this) {
    is BigDecimal -> this
    is Int, is Long, is Short, is Byte -> BigDecimal.valueOf(toLong())
    else -> BigDecimal(toString())
}

// --- Equality ---

infix fun <T : Any> Ex<T>.eq(other: Ex<T>): Ex<Boolean> = call(Operators.Eq, this, other)
infix fun <T : Any> Ex<T>.neq(other: Ex<T>): Ex<Boolean> = call(Operators.Neq, this, other)

infix fun Ex<String>.eq(value: String) = eq(lit(value))
infix fun Ex<String>.neq(value: String) = neq(lit(value))
infix fun Ex<BigDecimal>.eq(value: Number) = eq(lit(value))
infix fun Ex<BigDecimal>.neq(value: Number) = neq(lit(value))
infix fun Ex<Boolean>.eq(value: Boolean) = eq(lit(value))

// --- Numeric comparison ---

infix fun Ex<BigDecimal>.lt(other: Ex<BigDecimal>): Ex<Boolean> = call(Operators.Lt, this, other)
infix fun Ex<BigDecimal>.le(other: Ex<BigDecimal>): Ex<Boolean> = call(Operators.Le, this, other)
infix fun Ex<BigDecimal>.gt(other: Ex<BigDecimal>): Ex<Boolean> = call(Operators.Gt, this, other)
infix fun Ex<BigDecimal>.ge(other: Ex<BigDecimal>): Ex<Boolean> = call(Operators.Ge, this, other)

infix fun Ex<BigDecimal>.lt(value: Number) = lt(lit(value))
infix fun Ex<BigDecimal>.le(value: Number) = le(lit(value))
infix fun Ex<BigDecimal>.gt(value: Number) = gt(lit(value))
infix fun Ex<BigDecimal>.ge(value: Number) = ge(lit(value))

// --- Membership ---

infix fun Ex<String>.isIn(values: Collection<String>): Ex<Boolean> {
    require(values.isNotEmpty()) { "isIn requires at least one allowed value" }
    return call(Operators.In, this, Ex<Any>(Expr.Literal(Value.ListOf(values.map(Value::Str)))))
}

// --- Presence and blankness ---

// Blank checks accept optional strings too: a missing value counts as blank.
fun <S : String?> Ex<S>.isBlank(): Ex<Boolean> = call(Operators.IsBlank, this)
fun <S : String?> Ex<S>.isNotBlank(): Ex<Boolean> = call(Operators.IsNotBlank, this)
fun Ex<*>.isPresent(): Ex<Boolean> = call(Operators.IsPresent, this)

/** A missing value passes: `if office is present then office in {...}`. */
fun <T : Any> Ex<T?>.ifPresent(check: (Ex<T>) -> Ex<Boolean>): Ex<Boolean> = isPresent() implies check(Ex(node))

/** A missing value is a violation: `office is present and office in {...}`. */
fun <T : Any> Ex<T?>.presentAnd(check: (Ex<T>) -> Ex<Boolean>): Ex<Boolean> = isPresent() and check(Ex(node))

// --- Boolean logic ---

infix fun Ex<Boolean>.and(other: Ex<Boolean>): Ex<Boolean> = call(Operators.And, this, other)
infix fun Ex<Boolean>.or(other: Ex<Boolean>): Ex<Boolean> = call(Operators.Or, this, other)
operator fun Ex<Boolean>.not(): Ex<Boolean> = call(Operators.Not, this)
@JvmName("notOf")
fun not(expr: Ex<Boolean>): Ex<Boolean> = !expr

/** If this holds, [consequence] must hold too; otherwise the rule is satisfied. */
infix fun Ex<Boolean>.implies(consequence: Ex<Boolean>): Ex<Boolean> = call(Operators.Implies, this, consequence)

fun <T> ifElse(condition: Ex<Boolean>, then: Ex<T>, otherwise: Ex<T>): Ex<T> =
    call(Operators.IfElse, condition, then, otherwise)

fun allOf(first: Ex<Boolean>, vararg rest: Ex<Boolean>): Ex<Boolean> = rest.fold(first) { acc, e -> acc and e }
fun anyOf(first: Ex<Boolean>, vararg rest: Ex<Boolean>): Ex<Boolean> = rest.fold(first) { acc, e -> acc or e }

// --- Arithmetic ---

operator fun Ex<BigDecimal>.plus(other: Ex<BigDecimal>): Ex<BigDecimal> = call(Operators.Add, this, other)
operator fun Ex<BigDecimal>.minus(other: Ex<BigDecimal>): Ex<BigDecimal> = call(Operators.Sub, this, other)
operator fun Ex<BigDecimal>.times(other: Ex<BigDecimal>): Ex<BigDecimal> = call(Operators.Mul, this, other)
operator fun Ex<BigDecimal>.div(other: Ex<BigDecimal>): Ex<BigDecimal> = call(Operators.Div, this, other)

operator fun Ex<BigDecimal>.plus(value: Number) = plus(lit(value))
operator fun Ex<BigDecimal>.minus(value: Number) = minus(lit(value))
operator fun Ex<BigDecimal>.times(value: Number) = times(lit(value))
operator fun Ex<BigDecimal>.div(value: Number) = div(lit(value))
