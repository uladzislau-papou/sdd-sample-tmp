package com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception

/**
 * Raised when a master leasing contract or its terms would violate a rule in
 * `uc07-create-master-leasing-contract.spec.md` § 2.3.
 *
 * A **validation** exception in the taxonomy of `coding-style.definition.md` § 6.2, so it
 * maps to `BAD_REQUEST` on GraphQL (§ 3, § 9 of that spec).
 *
 * One exception type covers every § 2.3 rule rather than one per rule. That is what lets the
 * spec's § 3 error table stay complete while **PD-01** and **PD-10** are still provisional:
 * when a rule changes, only the *Condition* cell widens, and no consumer-visible error
 * classification moves.
 */
class InvalidMasterLeasingContractException(
    message: String,
) : RuntimeException(message)
