package rules

import rules.dsl.Ex
import rules.eval.Evaluator
import rules.eval.TypeChecker
import rules.model.EvalError
import rules.model.Expr
import rules.model.Type
import rules.model.Value
import rules.ops.Notation
import rules.ops.Operator
import rules.ops.OperatorRegistry
import rules.ops.Signature
import rules.render.Renderer
import rules.serialization.RuleStore
import rules.model.Rule
import rules.model.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Adding an operation means defining one [Operator] and registering it.
 * No change to the evaluator, renderer, type checker or serializer is needed.
 */
class ExtensibilityTest {
    private val startsWith = Operator(
        id = "startsWith",
        signature = Signature.fixed(Type.Str, Type.Str, result = Type.Bool),
        notation = Notation.Infix("starts with", precedence = 5, leftAssociative = false),
    ) { args -> Value.of(args.string(0).startsWith(args.string(1))) }

    private val registry = OperatorRegistry.Default + listOf(startsWith)

    private infix fun Ex<String>.startsWith(prefix: String): Ex<Boolean> =
        Ex(Expr.Call(startsWith.id, node, Expr.Literal(Value.Str(prefix))))

    @Test
    fun `a new operator works everywhere`() {
        val rule = Rule("prefix", "", (TestSchema.s startsWith "EMP-").node)
        val ruleSet = RuleSet(TestSchema.schemaId, listOf(rule))

        assertEquals("s starts with \"EMP-\"", Renderer(TestSchema, registry).render(rule.expression))
        assertEquals(emptyList(), TypeChecker(TestSchema, registry).check(rule.expression))

        val store = RuleStore(TestSchema, registry)
        assertEquals(ruleSet, store.decode(store.encode(ruleSet)))

        val evaluator = Evaluator(TestSchema, registry)
        assertEquals(Value.TRUE, evaluator.evaluate(rule.expression, mapOf("s" to "EMP-42")))
        assertEquals(Value.FALSE, evaluator.evaluate(rule.expression, mapOf("s" to "42")))
    }

    @Test
    fun `without registration the operator is reported as unknown`() {
        val expr = Expr.Call("startsWith", Expr.Field("s"), Expr.Literal(Value.Str("x")))
        val result = Evaluator(TestSchema).evaluate(Rule("r", "", expr), mapOf("s" to "x"))
        assertIs<rules.eval.RuleResult.Error>(result)
        assertEquals(EvalError.Code.UNKNOWN_OPERATOR, result.error.code)
    }
}
