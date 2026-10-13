package rules

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import rules.eval.Evaluator
import rules.eval.RuleResult
import rules.model.EvalError
import sandbox.Employee
import sandbox.EmployeeSchema
import sandbox.employeeRules
import kotlin.test.Test
import kotlin.test.assertEquals

class EmployeeRulesTest {
    private val evaluator = Evaluator(EmployeeSchema)
    private fun rule(id: String) = employeeRules.rules.single { it.id == id }

    private fun outcome(result: RuleResult) = when (result) {
        RuleResult.Passed -> "PASS"
        RuleResult.Violated -> "FAIL"
        is RuleResult.Skipped -> "SKIP"
        is RuleResult.Error -> result.error.code.name
    }

    // Columns: name, country, office, contract type, weekly hours, expected outcome of each of the three rules.
    @ParameterizedTest(name = "[{index}] {0}, {1}, {2}, {3}, {4}")
    @CsvSource(
        nullValues = ["null"],
        textBlock = """
        Anna Schmidt,   Germany,     Munich,    Full-time, 40,    PASS,      PASS,          PASS
        Bob Smith,      Netherlands, Amsterdam, Part-time, 24,    PASS,      PASS,          PASS
        '   ',          Germany,     Berlin,    Full-time, 40,    FAIL,      PASS,          PASS
        null,           Germany,     Berlin,    Full-time, 40,    FAIL,      PASS,          PASS
        Dmitry Ivanov,  Germany,     Prague,    Full-time, 40,    PASS,      FAIL,          PASS
        Eva Novak,      Czechia,     null,      Full-time, 38,    PASS,      PASS,          FAIL
        Frank Miller,   Germany,     Berlin,    Part-time, 40,    PASS,      PASS,          FAIL
        Gina Rossi,     Germany,     null,      Part-time, 20.5,  PASS,      FAIL,          PASS
        Ivan Petrov,    Poland,      null,      Full-time, 40,    PASS,      PASS,          PASS
        Julia Klein,    Germany,     Munich,    null,      30,    PASS,      PASS,          SKIP""",
    )
    fun `sample rules`(
        name: String?, country: String?, office: String?, contract: String?, hours: String?,
        nameRule: String, officeRule: String, hoursRule: String,
    ) {
        val record = Employee(name, country, office, contract, hours?.toBigDecimal()).toRecord()
        assertEquals(
            listOf(nameRule, officeRule, hoursRule),
            employeeRules.rules.map { outcome(evaluator.evaluate(it, record)) },
        )
    }

    @Test
    fun `a missing required field is reported once, by its own rule`() {
        val record = Employee("Julia Klein", "Germany", "Munich", null, 30).toRecord()
        val results = evaluator.evaluateAll(employeeRules, record).associate { (rule, result) -> rule.id to outcome(result) }
        assertEquals(
            mapOf(
                "name-required" to "PASS",
                "country-required" to "PASS",
                "contractType-required" to "FAIL",
                "weeklyHours-required" to "PASS",
                "name-not-blank" to "PASS",
                "german-office" to "PASS",
                "working-hours" to "SKIP",
            ),
            results,
        )
    }

    @Test
    fun `optional fields have no required rule`() {
        assertEquals(
            listOf("name-required", "country-required", "contractType-required", "weeklyHours-required"),
            EmployeeSchema.requiredFieldRules().map { it.id },
        )
    }

    @Test
    fun `field value of an incompatible type is reported as a type mismatch`() {
        val record = Employee("Hugo", "Austria", "Vienna", "Full-time", null).toRecord() + ("weeklyHours" to "forty")
        val result = evaluator.evaluate(rule("working-hours"), record)
        assertEquals(EvalError.Code.TYPE_MISMATCH, (result as RuleResult.Error).error.code)
        assertEquals(
            "field 'weeklyHours' should be number but the record contains String \"forty\"",
            result.error.message,
        )
    }

    @Test
    fun `a type error in a field the rule does not touch does not matter`() {
        val record = Employee("Hugo", "Austria", "Vienna", "Full-time", 40).toRecord() + ("country" to 42)
        assertEquals(RuleResult.Passed, evaluator.evaluate(rule("name-not-blank"), record))
    }
}
