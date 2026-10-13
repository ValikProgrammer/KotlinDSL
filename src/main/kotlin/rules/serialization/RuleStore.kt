package rules.serialization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import rules.eval.TypeChecker
import rules.model.RuleSet
import rules.ops.OperatorRegistry
import rules.schema.Schema
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class InvalidRuleSetException(val problems: List<String>) :
    RuntimeException("Invalid rule set:\n" + problems.joinToString("\n") { "  - $it" })

/**
 * Persists rule sets as JSON. Loading is validating: a file that refers to unknown fields or operators,
 * or that is ill-typed, is rejected with all problems listed instead of failing later during evaluation.
 */
class RuleStore(
    private val schema: Schema,
    operators: OperatorRegistry = OperatorRegistry.Default,
) {
    private val typeChecker = TypeChecker(schema, operators)

    fun encode(ruleSet: RuleSet): String {
        validate(ruleSet)
        return json.encodeToString(RuleSet.serializer(), ruleSet)
    }

    fun decode(text: String): RuleSet {
        val ruleSet = try {
            json.decodeFromString(RuleSet.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidRuleSetException(listOf("malformed rule file: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            throw InvalidRuleSetException(listOf("malformed rule file: ${e.message}"))
        }
        validate(ruleSet)
        return ruleSet
    }

    fun save(ruleSet: RuleSet, path: Path) = path.writeText(encode(ruleSet))

    fun load(path: Path): RuleSet = decode(path.readText())

    fun validate(ruleSet: RuleSet) {
        val problems = buildList {
            if (ruleSet.version != RuleSet.CURRENT_VERSION) {
                add("unsupported format version ${ruleSet.version}, expected ${RuleSet.CURRENT_VERSION}")
            }
            if (ruleSet.schema != schema.schemaId) add("rule set is for schema '${ruleSet.schema}', expected '${schema.schemaId}'")
            ruleSet.rules.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("duplicate rule id '$it'") }
            for (rule in ruleSet.rules) {
                typeChecker.check(rule.expression).forEach { add("rule '${rule.id}': $it") }
            }
        }
        if (problems.isNotEmpty()) throw InvalidRuleSetException(problems)
    }

    private companion object {
        val json = Json {
            prettyPrint = true
            classDiscriminator = "kind"
            encodeDefaults = true
        }
    }
}
