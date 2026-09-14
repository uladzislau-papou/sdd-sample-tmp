package com.jobradleasing.contractmanagement.shared.domain

/**
 * Identity of the Lessor (*Leasinggeber*), owned by an external partner master.
 *
 * SDD: See `documentation/modelling.definition.md` § Identity, category 3.
 */
data class LessorId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "lessorId must not be blank" }
    }
}
