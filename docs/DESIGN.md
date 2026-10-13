# Employee Validation Rules DSL: Design

## 1. Goal and the main trade-off

Rules must be *data*. A rule is an expression tree. Four independent components walk that tree: the **evaluator**
checks a record, the **renderer** produces text, the **serializer** writes and reads JSON, and the **type checker**
validates trees that did not come from Kotlin code.

The central decision is how operations are represented. Here are the three options I considered.

* **One class per operation, plus a branch for it in every component.** The compiler and the deserializer check the
  structure, but each new operation touches every component.
* **Each operation class evaluates and renders itself.** A new operation is one class, but evaluation and rendering live
  inside the data model.
* **Chosen: a generic call node, holding an operator id and its operands.** Each operation is a single `Operator` value
  (stable id, typing rule, notation, semantics) in a registry, and the components are generic. A new operation is one
  definition, and the file format never changes.

I believe new operations will be added far more often than new components and we don’t want to modify a bunch of things
while adding new operation. The price is that structural checks move from the compiler to our own type checker. A type
checker is needed in any design anyway, because only the schema knows that `weeklyHours` is a number.

## 2. The DSL

* `name.isNotBlank()`
* `(country eq "Germany") implies office.presentAnd { it isIn setOf("Munich", "Berlin") }`
* `ifElse(contractType eq "Full-time", then = weeklyHours eq 40, otherwise = weeklyHours lt 40)`

Fields are declared in a schema with their types. The DSL attaches phantom type to each expression, so
`weeklyHours eq "forty"` do not compile. Kotlin cannot overload `==` or `<` to build a tree, so comparisons are infix
functions. An infix function has exactly two sides, so the three-part conditional is a function with named arguments,
and no branch can be forgotten.

An optional field (office) cannot be compared until the author decides what a missing value means: `ifPresent` lets it
pass, `presentAnd` makes it a violation. Both are wrappers for existing operators.

Implemented: field and literal values, `= ≠ < ≤ > ≥`, `in`, `is blank`, `is present`, `and`, `or`, `not`,
`implies`, `if-then-else`, `+ - * /`.

## 3. Representation and evaluation

The tree has three node kinds: field, literal and call. It is plain immutable data, so two rules are equal exactly when
their trees are. Numbers are `BigDecimal`. I chose it because it’s accurate when comparing, although very heavy.

A record is a raw field map from the HR system, and it is not trusted. Values are converted to the declared types
without implicit coercion: `"40"` is not a number. `and`, `or`, `implies` and `if` short-circuit. This is part of the
semantics: the office of a non-German employee must never be read.

| Result | Meaning | Who acts |
|---|---|---|
| Passed | The record satisfies the rule | Nobody |
| Violated | The rule was checked and the data breaks it | HR fixes the record |
| Skipped | A required field is missing | HR, via that field's "is required" rule |
| Error | The rule could not be checked (wrong data type, division by zero, broken rule) | A developer |

Whether a field is required is declared in the schema, because it cannot be inferred from data. Each required field
gets an automatic "is required" rule, so a missing value is reported once, with its real cause. Other rules that need
the field are skipped instead of reporting misleading violations. Evaluation never throws, so one bad record does not
stop the report for the others.

## 4. Rendering

Each operator declares its notation (infix, prefix, postfix or keywords) and its precedence. The renderer adds
parentheses only where they are needed and uses field labels from the schema. The output is deterministic, for example
*if contract type = "Full-time" then weekly hours = 40 else weekly hours < 40*. For now the text is display-only.

## 5. Serialization

Rules are stored as JSON using kotlinx.serialization, the official Kotlin library. I chose JSON over YAML, whose typing
is ambiguous, and over a custom text format, which would need a parser. A web UI also reads JSON natively. Nodes are
tagged explicitly, numbers are stored as exact decimal text, and every file records its schema and a format version.
Loading validates the whole file and reports all problems at once. If any rule is invalid, the file is rejected.

## 6. Type safety and error handling

* **Compile time:** ill-typed rules, or an optional field used without deciding what a missing value means.
* **Load time:** unknown fields or operators, wrong number of operands, wrong operand types, a rule that is not boolean.
* **Per record:** a field value of an incompatible type (`TYPE_MISMATCH`, naming the field and the actual value),
  division by zero (`DIVISION_BY_ZERO`, naming the expression), a missing required field (`Skipped`).

## 7. Extending and next steps

**New operation:** register one `Operator` and add a one-line DSL function. The evaluator, renderer, type checker
and file format stay unchanged.

With more time I would do three things, in this order.

  1. **Dates**, because most real HR rules involve them. This step comes with two design decisions: which date format
     to accept from records, and how to store a date in JSON, which has no date type.
  2. **Explaining which part of a rule failed.** HR needs to know why a rule is violated, not only that it is. The
     evaluator would return a tree of results instead of a single value, which changes the evaluation interface of
     `Operator`.
  3. **An operator catalog for a UI editor.** The registry already knows every operator's id, operand types and
     notation. Exporting that list lets a UI offer only the operations that fit a field.

## 8. Deliberately left out

A text parser, dates, list fields and nested records, failure explanations and localized rendering.
