package rules.model

/** Why a rule could not be evaluated for a record. Evaluation errors are values, not crashes. */
data class EvalError(val code: Code, val message: String) {
    enum class Code {
        UNKNOWN_FIELD,
        UNKNOWN_OPERATOR,
        WRONG_ARITY,
        TYPE_MISMATCH,
        /** An optional field is missing and the rule did not say what that means. A bug in the rule. */
        MISSING_VALUE,
        /** A required field is missing. Reported by the field's own "is required" rule; other rules are skipped. */
        REQUIRED_FIELD_MISSING,
        DIVISION_BY_ZERO,
    }
}

class EvaluationException(val error: EvalError) : RuntimeException(error.message)
