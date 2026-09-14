package com.jobradleasing.contractmanagement.shared.domain

/**
 * Identity of the Employer (*Arbeitgeber*), owned by an external directory.
 *
 * SDD: See `documentation/modelling.definition.md` § Identity, category 3.
 */
data class EmployerId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "employerId must not be blank" }
    }
}
