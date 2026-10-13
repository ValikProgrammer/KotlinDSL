package rules

import rules.dsl.Ex
import rules.eval.Evaluator
import rules.model.EvalError
import rules.model.EvaluationException
import rules.model.Value
import rules.schema.Schema
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A schema with two required fields of every type plus optional ones, used by tests that are not about employees. */
object TestSchema : Schema("test") {
    val os = optionalString("os")
    val on = optionalNumber("on")
    val s = string("s")
    val t = string("t")
    val n = number("n")
    val m = number("m")
    val b = boolean("b")
    val c = boolean("c")
}

private val evaluator = Evaluator(TestSchema)

fun eval(expr: Ex<*>, vararg record: Pair<String, Any?>): Value = evaluator.evaluate(expr.node, mapOf(*record))

fun assertEvaluates(expected: Boolean, expr: Ex<Boolean>, vararg record: Pair<String, Any?>) =
    assertEquals(Value.of(expected), eval(expr, *record))

fun assertEvaluates(expected: String, expr: Ex<BigDecimal>, vararg record: Pair<String, Any?>) =
    assertEquals(Value.Num(BigDecimal(expected)), eval(expr, *record))

fun assertEvalError(expected: EvalError.Code, expr: Ex<*>, vararg record: Pair<String, Any?>): EvalError {
    val error = assertFailsWith<EvaluationException> { eval(expr, *record) }.error
    assertEquals(expected, error.code, error.message)
    return error
}
