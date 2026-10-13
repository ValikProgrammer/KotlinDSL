# Employee rules DSL

A small Kotlin DSL for employee data validation rules. The rules are stored as data, so the same rule can be:

* **built** in type-safe Kotlin,
* **evaluated** against employee records,
* **rendered** as text for a UI,
* **saved** to JSON and **restored**, with the same results after the round trip.

```kotlin
val employeeRules = ruleSet(EmployeeSchema) {
    rule("name-not-blank") { name.isNotBlank() }
    rule("german-office") { (country eq "Germany") implies office.presentAnd { it isIn setOf("Munich", "Berlin") } }
    rule("working-hours") {
        ifElse(contractType eq "Full-time", then = weeklyHours eq 40, otherwise = weeklyHours lt 40)
    }
}
// renders as: if contract type = "Full-time" then weekly hours = 40 else weekly hours < 40
```

The design, its trade-offs and the extension path are described in [docs/DESIGN.md](docs/DESIGN.md)
(the same content as a Word file: [docs/DESIGN.docx](docs/DESIGN.docx)).

## Requirements

JDK 17 or newer. Gradle is provided by the wrapper.

## Build and test

```sh
./gradlew build      # compile and run all tests
./gradlew test       # tests only; HTML report in build/reports/tests/test
```

## Sandbox

```sh
./gradlew run                            # writes rules.json in the project directory
./gradlew run --args="/tmp/rules.json"   # or to a path of your choice
```

The sandbox prints the sample rules as text, together with the "is required" rules derived from the schema. It then
evaluates them against ten employees, including records with a blank name, a wrong office, a missing optional office,
a missing required contract type and hours stored as text. It then saves the rules to JSON, loads them back
and checks that the results are identical.

## Layout

| Package | Responsibility |
|---|---|
| `rules.model` | `Expr`, `Value`, `Type`, `Rule`, `RuleSet`, `EvalError`: plain data |
| `rules.ops` | `Operator` (id, signature, notation, semantics), the built-in catalog `Operators`, `OperatorRegistry` |
| `rules.dsl` | Typed builder: `Ex<T>`, infix functions, `ruleSet { rule { } }` |
| `rules.schema` | `Schema`: typed required/optional field declarations, derived "is required" rules, reading raw record values |
| `rules.eval` | `Evaluator` (records → `Passed`/`Violated`/`Skipped`/`Error`), `TypeChecker` (static check of loaded trees) |
| `rules.render` | `Renderer`: precedence-aware, deterministic text |
| `rules.serialization` | `RuleStore` (validating JSON load/save) and the literal value serializer |
| `sandbox` | `EmployeeSchema`, the sample rules, and the `main` demo |

## Tests

`src/test/kotlin/rules` contains:

* `OperatorsTest`: semantics of every operator, boundaries, short-circuiting, exact decimals, division by zero, missing values.
* `EmployeeRulesTest`: the sample rules on a table of employees, required fields, and field values of the wrong type.
* `EvaluatorTest`, `TypeCheckerTest`: malformed trees (unknown fields or operators, wrong arity, type errors).
* `RendererTest`: exact rendered text, precedence and parentheses, escaping.
* `SerializationTest`: file round trip with identical evaluation, a snapshot of the format, rejection of invalid files.
* `ExtensibilityTest`: a new operator added without touching the engine.

CI (`.github/workflows/ci.yml`) runs `./gradlew build` and the sandbox on every push and pull request.

## Dependencies

* `kotlinx-serialization-json`: the official Kotlin serialization library. It uses a compiler plugin instead of
  reflection and supports sealed hierarchies.
* `kotlin-test` with JUnit 5, and `junit-jupiter-params` for table-driven tests.
