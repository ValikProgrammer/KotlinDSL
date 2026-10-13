package rules

import rules.dsl.eq
import rules.dsl.ifPresent
import rules.dsl.isPresent
import rules.dsl.presentAnd
import rules.dsl.isIn
import rules.dsl.ruleSet
import rules.model.Expr
import rules.model.Value
import sandbox.EmployeeSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DslTest {
    @Test
    fun `DSL builds a plain data tree`() {
        val rules = ruleSet(EmployeeSchema) {
            rule("german-country") { country isIn setOf("Germany", "Austria") }
        }
        assertEquals(
            Expr.Call("in", Expr.Field("country"), Expr.Literal(Value.ListOf(listOf(Value.Str("Germany"), Value.Str("Austria"))))),
            rules.rules.single().expression,
        )
        assertEquals("employee", rules.schema)
    }

    @Test
    fun `rule ids must be unique`() {
        assertFailsWith<IllegalArgumentException> {
            ruleSet(EmployeeSchema) {
                rule("a") { name eq "x" }
                rule("a") { name eq "y" }
            }
        }
    }

    @Test
    fun `isIn requires at least one value`() {
        assertFailsWith<IllegalArgumentException> { ruleSet(EmployeeSchema) { rule("a") { country isIn emptyList() } } }
    }

    @Test
    fun `optional fields are unwrapped explicitly, choosing what missing means`() {
        val rules = ruleSet(EmployeeSchema) {
            rule("lenient") { office.ifPresent { it eq "Munich" } }
            rule("strict") { office.presentAnd { it eq "Munich" } }
        }
        val isMunich = Expr.Call("eq", Expr.Field("office"), Expr.Literal(Value.Str("Munich")))
        val present = Expr.Call("isPresent", Expr.Field("office"))
        assertEquals(
            listOf(Expr.Call("implies", present, isMunich), Expr.Call("and", present, isMunich)),
            rules.rules.map { it.expression },
        )
    }

    // Type safety of the DSL is enforced by the compiler, so these lines are kept as documentation:
    //   weeklyHours eq "forty"        // no overload of eq(Ex<BigDecimal>, String)
    //   name lt 40                    // lt is only defined on Ex<BigDecimal>
    //   name eq weeklyHours           // Ex is invariant: Ex<String> vs Ex<BigDecimal>
    //   rule("r") { weeklyHours + 1 } // a rule body must be Ex<Boolean>
    //   office eq "Munich"            // office is optional (Ex<String?>): use ifPresent / presentAnd first
}
