package rules

import rules.eval.TypeChecker
import rules.model.Expr
import rules.model.Type
import rules.model.Value
import sandbox.EmployeeSchema
import sandbox.employeeRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeCheckerTest {
    private val checker = TypeChecker(TestSchema)
    private val s = Expr.Field("s")
    private val n = Expr.Field("n")
    private val b = Expr.Field("b")
    private fun str(v: String) = Expr.Literal(Value.Str(v))
    private fun num(v: Long) = Expr.Literal(Value.Num(v))

    @Test
    fun `rules built with the DSL are well-typed`() {
        val employeeChecker = TypeChecker(EmployeeSchema)
        employeeRules.rules.forEach { assertEquals(emptyList(), employeeChecker.check(it.expression)) }
    }

    @Test
    fun `reports unknown fields and operators`() {
        assertEquals(listOf("unknown field 'salary'"), checker.check(Expr.Call("isPresent", Expr.Field("salary"))))
        assertEquals(listOf("unknown operator 'matches'"), checker.check(Expr.Call("matches", s)))
    }

    @Test
    fun `reports operand type mismatches`() {
        assertEquals(
            listOf("'lt' expects (number, number) -> boolean but got (string, number): s < 40"),
            checker.check(Expr.Call("lt", s, num(40))),
        )
        assertEquals(1, checker.check(Expr.Call("eq", s, num(1))).size)
        assertEquals(1, checker.check(Expr.Call("in", s, Expr.Literal(Value.ListOf(listOf(Value.Num(1)))))).size)
        assertEquals(1, checker.check(Expr.Call("if", b, s, n), expected = Type.Str).size)
    }

    @Test
    fun `reports a non-boolean rule`() {
        assertEquals(listOf("expression has type number, expected boolean: n + 1"), checker.check(Expr.Call("add", n, num(1))))
    }

    @Test
    fun `rejects empty and mixed list literals`() {
        val empty = Expr.Call("in", s, Expr.Literal(Value.ListOf(emptyList())))
        val mixed = Expr.Call("in", s, Expr.Literal(Value.ListOf(listOf(Value.Str("a"), Value.Num(1)))))
        assertTrue(checker.check(empty).single().startsWith("literal {}"))
        assertTrue(checker.check(mixed).single().startsWith("literal {\"a\", 1}"))
    }

    @Test
    fun `collects all problems instead of stopping at the first`() {
        val expr = Expr.Call("and", Expr.Call("lt", s, num(1)), Expr.Call("eq", Expr.Field("x"), str("y")))
        assertEquals(2, checker.check(expr).size)
    }
}
