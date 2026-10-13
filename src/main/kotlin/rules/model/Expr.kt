package rules.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Untyped expression tree: the single source of truth that is evaluated, rendered and persisted.
 *
 * The tree is intentionally generic: every operation is a [Call] that names an [Operator] by its stable id.
 * Adding an operation therefore never adds a node class, so the serializer and the tree-walking
 * code in the evaluator, renderer and type checker stay unchanged.
 */
@Serializable
sealed interface Expr {
    @Serializable
    @SerialName("field")
    data class Field(val name: String) : Expr

    @Serializable
    @SerialName("literal")
    data class Literal(val value: Value) : Expr

    @Serializable
    @SerialName("call")
    data class Call(val op: String, val args: List<Expr>) : Expr {
        constructor(op: String, vararg args: Expr) : this(op, args.toList())
    }
}
