package sandbox

import rules.dsl.eq
import rules.dsl.ifElse
import rules.dsl.implies
import rules.dsl.isIn
import rules.dsl.isNotBlank
import rules.dsl.lt
import rules.dsl.presentAnd
import rules.dsl.ruleSet

/** The three sample rules from the assignment. */
val employeeRules = ruleSet(EmployeeSchema) {
    rule("name-not-blank", "The employee's name must not be blank") {
        name.isNotBlank()
    }
    rule("german-office", "Employees in Germany must be in the Munich or Berlin office") {
        (country eq "Germany") implies office.presentAnd { it isIn setOf("Munich", "Berlin") }
    }
    rule("working-hours", "Full-time means exactly 40 hours a week, anything else means fewer") {
        ifElse(
            contractType eq "Full-time",
            then = weeklyHours eq 40,
            otherwise = weeklyHours lt 40,
        )
    }
}
