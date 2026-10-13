package sandbox

import rules.schema.Record
import rules.schema.Schema

object EmployeeSchema : Schema("employee") {
    val name = string("name")
    val country = string("country")
    /** Optional: e.g. remote employees have no office. */
    val office = optionalString("office")
    val contractType = string("contractType")
    val weeklyHours = number("weeklyHours")
}

data class Employee(
    val name: String?,
    val country: String?,
    val office: String?,
    val contractType: String?,
    val weeklyHours: Number?,
) {
    fun toRecord(): Record = mapOf(
        "name" to name,
        "country" to country,
        "office" to office,
        "contractType" to contractType,
        "weeklyHours" to weeklyHours,
    )
}
