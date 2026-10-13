package rules

import rules.TestSchema.b
import rules.TestSchema.c
import rules.TestSchema.m
import rules.TestSchema.n
import rules.TestSchema.on
import rules.TestSchema.os
import rules.TestSchema.s
import rules.TestSchema.t
import rules.dsl.allOf
import rules.dsl.and
import rules.dsl.anyOf
import rules.dsl.div
import rules.dsl.eq
import rules.dsl.ge
import rules.dsl.gt
import rules.dsl.ifElse
import rules.dsl.ifPresent
import rules.dsl.presentAnd
import rules.dsl.implies
import rules.dsl.isBlank
import rules.dsl.isIn
import rules.dsl.isNotBlank
import rules.dsl.isPresent
import rules.dsl.le
import rules.dsl.lit
import rules.dsl.lt
import rules.dsl.minus
import rules.dsl.neq
import rules.dsl.not
import rules.dsl.or
import rules.dsl.plus
import rules.dsl.times
import rules.model.EvalError.Code
import kotlin.test.Test
import kotlin.test.assertContains

class OperatorsTest {
    @Test
    fun `equality on strings is exact and case sensitive`() {
        assertEvaluates(true, s eq "Germany", "s" to "Germany")
        assertEvaluates(false, s eq "Germany", "s" to "germany")
        assertEvaluates(true, s neq "Germany", "s" to "France")
        assertEvaluates(true, s eq t, "s" to "x", "t" to "x")
    }

    @Test
    fun `numbers compare by value regardless of representation`() {
        assertEvaluates(true, n eq 40, "n" to 40.0)
        assertEvaluates(true, n eq 40, "n" to 40L)
        assertEvaluates(true, n eq 37.5, "n" to "37.50".toBigDecimal())
        assertEvaluates(false, n neq 40, "n" to 40)
    }

    @Test
    fun `booleans compare`() {
        assertEvaluates(true, b eq true, "b" to true)
        assertEvaluates(false, b eq c, "b" to true, "c" to false)
    }

    @Test
    fun `numeric comparisons including boundaries`() {
        val cases = listOf(39, 40, 41)
        for (value in cases) {
            val r = arrayOf("n" to value)
            assertEvaluates(value < 40, n lt 40, *r)
            assertEvaluates(value <= 40, n le 40, *r)
            assertEvaluates(value > 40, n gt 40, *r)
            assertEvaluates(value >= 40, n ge 40, *r)
        }
        assertEvaluates(true, n lt m, "n" to -1.5, "m" to 0)
    }

    @Test
    fun `membership in a set of allowed values`() {
        val inOffices = s isIn listOf("Munich", "Berlin")
        assertEvaluates(true, inOffices, "s" to "Berlin")
        assertEvaluates(false, inOffices, "s" to "Prague")
        assertEvaluates(false, inOffices, "s" to "munich")
    }

    @Test
    fun `blank checks treat missing, empty and whitespace as blank`() {
        for (blank in listOf(null, "", "   ", "\t\n")) {
            assertEvaluates(true, s.isBlank(), "s" to blank)
            assertEvaluates(false, s.isNotBlank(), "s" to blank)
        }
        assertEvaluates(false, s.isBlank(), "s" to " a ")
    }

    @Test
    fun `presence check accepts any type`() {
        assertEvaluates(true, n.isPresent(), "n" to 0)
        assertEvaluates(false, n.isPresent())
        assertEvaluates(true, s.isPresent(), "s" to "")
    }

    @Test
    fun `boolean composition truth tables`() {
        for (x in listOf(true, false)) for (y in listOf(true, false)) {
            val r = arrayOf("b" to x, "c" to y)
            assertEvaluates(x && y, b and c, *r)
            assertEvaluates(x || y, b or c, *r)
            assertEvaluates(!x || y, b implies c, *r)
            assertEvaluates(!x, !b, *r)
            assertEvaluates(!x, not(b), *r)
        }
    }

    @Test
    fun `allOf and anyOf fold over and and or`() {
        assertEvaluates(true, allOf(b, c, lit(true)), "b" to true, "c" to true)
        assertEvaluates(false, allOf(b, c), "b" to true, "c" to false)
        assertEvaluates(true, anyOf(b, c), "b" to false, "c" to true)
    }

    @Test
    fun `and, or and implies short-circuit so the right side may be missing`() {
        val divByZero = (n / 0) gt 1
        assertEvaluates(false, (b eq true) and divByZero, "b" to false, "n" to 1)
        assertEvaluates(true, (b eq true) or divByZero, "b" to true, "n" to 1)
        assertEvaluates(true, (b eq true) implies divByZero, "b" to false, "n" to 1)
        assertEvaluates(true, (s eq "Germany") implies (t isIn listOf("Munich")), "s" to "France")
    }

    @Test
    fun `if-else evaluates only the chosen branch`() {
        val hours = ifElse(s eq "Full-time", then = n eq 40, otherwise = n lt 40)
        assertEvaluates(true, hours, "s" to "Full-time", "n" to 40)
        assertEvaluates(false, hours, "s" to "Full-time", "n" to 38)
        assertEvaluates(true, hours, "s" to "Part-time", "n" to 38)
        assertEvaluates(false, hours, "s" to "Part-time", "n" to 40)
        assertEvaluates("1", ifElse(b, then = n / 0, otherwise = lit(1)), "b" to false, "n" to 5)
    }

    @Test
    fun `arithmetic is exact decimal arithmetic`() {
        assertEvaluates("0.3", n + m, "n" to "0.1".toBigDecimal(), "m" to "0.2".toBigDecimal())
        assertEvaluates("-2", n - m, "n" to 3, "m" to 5)
        assertEvaluates("37.5", n * 0.5, "n" to 75)
        assertEvaluates("2.5", n / 2, "n" to 5)
        assertEvaluates(true, (n * 4) / 3 gt 13.33, "n" to 10)
    }

    @Test
    fun `division by zero is an evaluation error, not an exception leaking out`() {
        val error = assertEvalError(Code.DIVISION_BY_ZERO, n / m gt 1, "n" to 1, "m" to 0.0)
        assertContains(error.message, "m is 0")
    }

    @Test
    fun `a missing required field stops the rule`() {
        assertEvalError(Code.REQUIRED_FIELD_MISSING, s eq "x")
        assertEvalError(Code.REQUIRED_FIELD_MISSING, n lt 40)
        assertEvalError(Code.REQUIRED_FIELD_MISSING, s isIn listOf("a"))
        assertEvalError(Code.REQUIRED_FIELD_MISSING, n + 1)
        assertEvalError(Code.REQUIRED_FIELD_MISSING, b and c, "b" to true)
    }

    @Test
    fun `ifPresent lets a missing optional value pass, presentAnd makes it a violation`() {
        assertEvaluates(true, os.ifPresent { it eq "x" })
        assertEvaluates(false, os.presentAnd { it eq "x" })
        for (check in listOf(os.ifPresent { it eq "x" }, os.presentAnd { it eq "x" })) {
            assertEvaluates(true, check, "os" to "x")
            assertEvaluates(false, check, "os" to "y")
        }
        assertEvaluates(true, on.ifPresent { it + 1 gt 1 }, "on" to 1)
        assertEvaluates(true, os.isBlank())
    }
}
