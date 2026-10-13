package rules.model

import kotlinx.serialization.Serializable

@Serializable
data class Rule(
    val id: String,
    val description: String,
    val expression: Expr,
)

/** A set of rules written against one [rules.schema.Schema], identified by its name. */
@Serializable
data class RuleSet(
    val schema: String,
    val rules: List<Rule>,
    /** Format version of the persisted file, to allow migrations later. */
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
