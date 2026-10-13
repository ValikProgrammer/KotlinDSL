package rules.dsl

import rules.model.Expr

/**
 * Typed handle over an untyped [Expr] node. [T] is a phantom type that exists only at compile time:
 * it lets Kotlin reject `weeklyHours eq "forty"` or `name lt 3` while the stored tree stays plain data.
 *
 * [T] is invariant on purpose: with `out T`, `Ex<String> eq Ex<BigDecimal>` would type-check as `Ex<Any>`.
 */
class Ex<T> internal constructor(val node: Expr) {
    override fun equals(other: Any?) = other is Ex<*> && node == other.node
    override fun hashCode() = node.hashCode()
    override fun toString() = "Ex($node)"
}
