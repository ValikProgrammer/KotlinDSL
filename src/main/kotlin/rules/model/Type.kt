package rules.model

sealed interface Type {
    data object Str : Type {
        override fun toString() = "string"
    }

    data object Num : Type {
        override fun toString() = "number"
    }

    data object Bool : Type {
        override fun toString() = "boolean"
    }

    data class ListOf(val element: Type) : Type {
        override fun toString() = "list<$element>"
    }
}
