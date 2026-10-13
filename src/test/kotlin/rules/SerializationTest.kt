package rules

import org.junit.jupiter.api.io.TempDir
import rules.dsl.Ex
import rules.dsl.and
import rules.dsl.div
import rules.dsl.eq
import rules.dsl.ge
import rules.dsl.ifElse
import rules.dsl.isIn
import rules.dsl.isPresent
import rules.dsl.lit
import rules.dsl.not
import rules.dsl.or
import rules.dsl.plus
import rules.dsl.ruleSet
import rules.eval.Evaluator
import rules.serialization.InvalidRuleSetException
import rules.serialization.RuleStore
import sandbox.EmployeeSchema
import sandbox.employeeRules
import sandbox.sampleEmployees
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializationTest {
    private val store = RuleStore(EmployeeSchema)
    private val evaluator = Evaluator(EmployeeSchema)

    @Test
    fun `sample rules survive a file round trip and evaluate identically`(@TempDir dir: Path) {
        val file = dir.resolve("rules.json")
        store.save(employeeRules, file)
        val restored = store.load(file)

        assertEquals(employeeRules, restored)
        for ((label, record) in sampleEmployees) {
            assertEquals(
                employeeRules.rules.map { evaluator.evaluate(it, record) },
                restored.rules.map { evaluator.evaluate(it, record) },
                label,
            )
        }
    }

    @Test
    fun `every operator and literal kind round-trips`() {
        val schema = TestSchema
        val rules = ruleSet(schema) {
            rule("everything", "uses every operator") {
                val arithmetic: Ex<java.math.BigDecimal> = ifElse(b, n + m, n / lit(3))
                (arithmetic ge 0.1) and !(s isIn listOf("x", "y \"quoted\"")) or (b eq false) or c.isPresent()
            }
        }
        val testStore = RuleStore(schema)
        assertEquals(rules, testStore.decode(testStore.encode(rules)))
    }

    @Test
    fun `numbers are stored exactly`() {
        val rules = ruleSet(TestSchema) { rule("precise") { n eq "0.1000000000000000000000001".toBigDecimal() } }
        val testStore = RuleStore(TestSchema)
        val json = testStore.encode(rules)
        assertContains(json, "\"value\": 0.1000000000000000000000001")
        assertEquals(rules, testStore.decode(json))
    }

    @Test
    fun `stored format is stable and readable`() {
        val rules = ruleSet(EmployeeSchema) { rule("r", "d") { country eq "Germany" } }
        assertEquals(
            """
            {
                "schema": "employee",
                "rules": [
                    {
                        "id": "r",
                        "description": "d",
                        "expression": {
                            "kind": "call",
                            "op": "eq",
                            "args": [
                                {
                                    "kind": "field",
                                    "name": "country"
                                },
                                {
                                    "kind": "literal",
                                    "value": "Germany"
                                }
                            ]
                        }
                    }
                ],
                "version": 1
            }
            """.trimIndent(),
            store.encode(rules),
        )
    }

    @Test
    fun `loading rejects ill-typed rules with a clear message`(@TempDir dir: Path) {
        val file = dir.resolve("broken.json")
        file.writeText(
            """
            {"schema": "employee", "version": 1, "rules": [
              {"id": "a", "description": "", "expression":
                {"kind": "call", "op": "lt", "args": [{"kind": "field", "name": "name"}, {"kind": "literal", "value": 40}]}},
              {"id": "b", "description": "", "expression":
                {"kind": "call", "op": "startsWith", "args": [{"kind": "field", "name": "salary"}]}}
            ]}
            """.trimIndent(),
        )
        val e = assertFailsWith<InvalidRuleSetException> { store.load(file) }
        assertEquals(
            listOf(
                "rule 'a': 'lt' expects (number, number) -> boolean but got (string, number): name < 40",
                "rule 'b': unknown field 'salary'",
                "rule 'b': unknown operator 'startsWith'",
            ),
            e.problems,
        )
    }

    @Test
    fun `loading rejects malformed files, other schemas and unknown versions`() {
        fun problems(json: String) = assertFailsWith<InvalidRuleSetException> { store.decode(json) }.problems

        assertContains(problems("{not json").single(), "malformed rule file")
        assertContains(problems("""{"schema": "employee", "rules": [{"id": "a"}]}""").single(), "malformed rule file")
        assertContains(
            problems("""{"schema": "employee", "rules": [{"id": "a", "description": "", "expression": {"kind": "literal", "value": {"x": 1}}}]}""").single(),
            "malformed rule file",
        )
        assertEquals(listOf("rule set is for schema 'test', expected 'employee'"), problems("""{"schema": "test", "rules": []}"""))
        assertEquals(listOf("unsupported format version 2, expected 1"), problems("""{"schema": "employee", "rules": [], "version": 2}"""))
    }
}
