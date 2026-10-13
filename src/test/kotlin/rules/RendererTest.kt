package rules

import rules.TestSchema.b
import rules.TestSchema.c
import rules.TestSchema.m
import rules.TestSchema.n
import rules.TestSchema.s
import rules.dsl.Ex
import rules.dsl.and
import rules.dsl.div
import rules.dsl.eq
import rules.dsl.ifElse
import rules.dsl.implies
import rules.dsl.isBlank
import rules.dsl.isIn
import rules.dsl.lit
import rules.dsl.lt
import rules.dsl.minus
import rules.dsl.not
import rules.dsl.or
import rules.dsl.plus
import rules.dsl.times
import rules.model.Expr
import rules.render.Renderer
import sandbox.EmployeeSchema
import sandbox.employeeRules
import kotlin.test.Test
import kotlin.test.assertEquals

class RendererTest {
    private val renderer = Renderer(TestSchema)
    private fun render(expr: Ex<*>) = renderer.render(expr.node)

    @Test
    fun `sample rules render as readable text with field labels`() {
        val employeeRenderer = Renderer(EmployeeSchema)
        assertEquals(
            listOf(
                "name is not blank",
                "if country = \"Germany\" then office is present and office in {\"Munich\", \"Berlin\"}",
                "if contract type = \"Full-time\" then weekly hours = 40 else weekly hours < 40",
            ),
            employeeRules.rules.map { employeeRenderer.render(it.expression) },
        )
    }

    @Test
    fun `parentheses only where precedence requires them`() {
        assertEquals("n + m * 2", render(n + m * 2))
        assertEquals("(n + m) * 2", render((n + m) * 2))
        assertEquals("n - m - 1", render(n - m - 1))
        assertEquals("n - (m - 1)", render(n - (m - 1)))
        assertEquals("n / (m * 2)", render(n / (m * 2)))
        assertEquals("b and c or b", render((b and c) or b))
        assertEquals("b and (c or b)", render(b and (c or b)))
        assertEquals("n + 1 < m", render(n + 1 lt m))
    }

    @Test
    fun `not, postfix and conditionals`() {
        assertEquals("not b", render(!b))
        assertEquals("not (s is blank)", render(!s.isBlank()))
        assertEquals("not (b and c)", render(!(b and c)))
        assertEquals("if b then c", render(b implies c))
        assertEquals("if (if b then c) then b", render((b implies c) implies b))
        assertEquals("if b and c then n else m", render(ifElse(b and c, n, m)))
        assertEquals("if b then (if c then n else m) else 0", render(ifElse(b, ifElse(c, n, m), lit(0))))
    }

    @Test
    fun `literals are rendered unambiguously`() {
        assertEquals("s = \"say \\\"hi\\\"\\n\"", render(s eq "say \"hi\"\n"))
        assertEquals("n = 37.5", render(n eq 37.50))
        assertEquals("n = 1000000", render(n eq 1e6))
        assertEquals("b = false", render(b eq false))
        assertEquals("s in {\"b\", \"a\"}", render(s isIn listOf("b", "a")))
    }

    @Test
    fun `unknown operators are still rendered`() {
        assertEquals("matches(s, \"x\")", renderer.render(Expr.Call("matches", s.node, lit("x").node)))
    }
}
