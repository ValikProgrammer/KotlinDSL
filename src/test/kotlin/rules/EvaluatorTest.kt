package rules

import rules.eval.Evaluator
import rules.eval.RuleResult
import rules.model.EvalError.Code
import rules.model.Expr
import rules.model.Rule
import rules.model.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Trees that the typed DSL cannot produce but a hand-edited file could; evaluation must still fail gracefully. */
class EvaluatorTest {
    private val evaluator = Evaluator(TestSchema)

    private fun run(expr: Expr, record: Map<String, Any?> = emptyMap()) =
        evaluator.evaluate(Rule("r", "", expr), record)

    private fun assertError(code: Code, expr: Expr, record: Map<String, Any?> = emptyMap()) {
        val result = run(expr, record)
        assertIs<RuleResult.Error>(result)
        assertEquals(code, result.error.code, result.error.message)
    }

    private val s = Expr.Field("s")
    private val n = Expr.Field("n")
    private fun lit(v: String) = Expr.Literal(Value.Str(v))
    private fun lit(v: Long) = Expr.Literal(Value.Num(v))

    @Test
    fun `unknown field`() = assertError(Code.UNKNOWN_FIELD, Expr.Call("isPresent", Expr.Field("salary")))

    @Test
    fun `unknown operator`() = assertError(Code.UNKNOWN_OPERATOR, Expr.Call("matches", s, lit("x")))

    @Test
    fun `wrong number of operands`() = assertError(Code.WRONG_ARITY, Expr.Call("eq", s))

    @Test
    fun `comparing values of different types`() =
        assertError(Code.TYPE_MISMATCH, Expr.Call("eq", s, lit(1)), mapOf("s" to "1"))

    @Test
    fun `arithmetic on a string`() =
        assertError(Code.TYPE_MISMATCH, Expr.Call("lt", s, lit(1)), mapOf("s" to "1"))

    @Test
    fun `rule that does not produce a boolean`() =
        assertError(Code.TYPE_MISMATCH, Expr.Call("add", n, lit(1)), mapOf("n" to 1))

    @Test
    fun `error message points at the failing sub-expression`() {
        val result = run(Expr.Call("lt", s, lit(1)), mapOf("s" to "1")) as RuleResult.Error
        assertEquals("s is string, expected number (in: s < 1)", result.error.message)
    }

    @Test
    fun `an unhandled missing optional value is a bug in the rule`() =
        assertError(Code.MISSING_VALUE, Expr.Call("eq", Expr.Field("os"), lit("x")))

    @Test
    fun `NaN is not a valid number`() =
        assertError(Code.TYPE_MISMATCH, Expr.Call("isPresent", n), mapOf("n" to Double.NaN))
}
