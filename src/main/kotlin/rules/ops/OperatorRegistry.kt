package rules.ops

class OperatorRegistry(operators: Iterable<Operator>) {
    private val byId: Map<String, Operator> = buildMap {
        for (op in operators) {
            require(put(op.id, op) == null) { "Duplicate operator id '${op.id}'" }
        }
    }

    operator fun get(id: String): Operator? = byId[id]

    val operators: Collection<Operator> get() = byId.values

    operator fun plus(other: Iterable<Operator>) = OperatorRegistry(operators + other)

    companion object {
        val Default = OperatorRegistry(Operators.all)
    }
}
