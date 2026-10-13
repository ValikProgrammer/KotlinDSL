package rules.dsl

import rules.model.Rule
import rules.model.RuleSet
import rules.schema.Schema

@DslMarker
annotation class RuleDsl

@RuleDsl
class RuleSetBuilder<S : Schema> internal constructor(private val schema: S) {
    private val rules = mutableListOf<Rule>()

    fun rule(id: String, description: String = "", body: S.() -> Ex<Boolean>) {
        require(rules.none { it.id == id }) { "Duplicate rule id '$id'" }
        rules += Rule(id, description, schema.body().node)
    }

    internal fun build() = RuleSet(schema.schemaId, rules.toList())
}

fun <S : Schema> ruleSet(schema: S, block: RuleSetBuilder<S>.() -> Unit): RuleSet =
    RuleSetBuilder(schema).apply(block).build()
