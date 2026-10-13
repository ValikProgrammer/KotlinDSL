package sandbox

import rules.eval.Evaluator
import rules.eval.RuleResult
import rules.render.Renderer
import rules.schema.Record
import rules.serialization.RuleStore
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.readText

val sampleEmployees: List<Pair<String, Record>> = listOf(
    "Anna (valid, Munich)" to Employee("Anna Schmidt", "Germany", "Munich", "Full-time", 40).toRecord(),
    "Bob (valid, part-time)" to Employee("Bob Smith", "Netherlands", "Amsterdam", "Part-time", 24).toRecord(),
    "Clara (blank name)" to Employee("   ", "Germany", "Berlin", "Full-time", 40).toRecord(),
    "Dmitry (Germany, wrong office)" to Employee("Dmitry Ivanov", "Germany", "Prague", "Full-time", 40).toRecord(),
    "Eva (full-time, 38h)" to Employee("Eva Novak", "Czechia", "Prague", "Full-time", 38).toRecord(),
    "Frank (part-time, 40h)" to Employee("Frank Miller", "Germany", "Berlin", "Part-time", 40).toRecord(),
    "Gina (Germany, no office)" to Employee("Gina Rossi", "Germany", null, "Part-time", 20.5).toRecord(),
    "Ivan (remote, no office)" to Employee("Ivan Petrov", "Poland", null, "Full-time", 40).toRecord(),
    "Julia (no contract type)" to Employee("Julia Klein", "Germany", "Munich", null, 30).toRecord(),
    // Raw data from an external system can contain anything: hours arrive as text here.
    "Hugo (hours as text)" to Employee("Hugo Weber", "Austria", "Vienna", "Full-time", null).toRecord() +
        ("weeklyHours" to "forty"),
)

fun main(args: Array<String>) {
    val file: Path = Path(args.firstOrNull() ?: "rules.json").absolute()
    val renderer = Renderer(EmployeeSchema)
    val evaluator = Evaluator(EmployeeSchema)
    val store = RuleStore(EmployeeSchema)

    println("== Rules (required-field rules come from the schema) ==")
    for (rule in EmployeeSchema.requiredFieldRules() + employeeRules.rules) {
        println("[${rule.id}] ${rule.description}")
        println("    ${renderer.render(rule.expression)}")
    }

    println("\n== Evaluation ==")
    val before = sampleEmployees.associate { (label, record) ->
        label to evaluator.evaluateAll(employeeRules, record)
    }
    for ((label, results) in before) {
        println(label)
        // Passed required-field checks are noise; show only the interesting ones.
        results.filterNot { (rule, result) -> rule.id.endsWith("-required") && result == RuleResult.Passed }
            .forEach { (rule, result) -> println("    ${rule.id.padEnd(22)} ${format(result)}") }
    }

    println("\n== Persistence ==")
    store.save(employeeRules, file)
    println("Saved ${employeeRules.rules.size} rules to $file")
    val restored = store.load(file)
    println("Restored rules are equal to the originals: ${restored == employeeRules}")
    val after = sampleEmployees.associate { (label, record) ->
        label to evaluator.evaluateAll(restored, record)
    }
    println("Results after the round trip are identical: ${before == after}")
    println("\nBeginning of $file:")
    println(file.readText().lineSequence().take(25).joinToString("\n") + "\n  ...")
}

private fun format(result: RuleResult) = when (result) {
    RuleResult.Passed -> "PASSED"
    RuleResult.Violated -> "VIOLATED"
    is RuleResult.Skipped -> "SKIPPED (${result.reason})"
    is RuleResult.Error -> "ERROR ${result.error.code}: ${result.error.message}"
}
