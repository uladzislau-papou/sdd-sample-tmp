package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * The currency a contract's monetary terms are denominated in [*Währung*].
 *
 * The source gives the German term and nothing else — no format, no set — so the
 * three-uppercase-letter shape is **PD-10**.
 *
 * Checked as a *shape* and not against a currency table, deliberately. A table would be a
 * data dependency with no source, and it would conflate two different failures: a malformed
 * input, which is ours to reject, and a well-formed code we do not support, which is a
 * business question. Only the first is validation.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class CurrencyCode private constructor(
    val value: String,
) {
    companion object {
        private val PATTERN = Regex("[A-Z]{3}")

        operator fun invoke(value: String): CurrencyCode {
            if (!PATTERN.matches(value)) {
                throw InvalidMasterLeasingContractException(
                    "currency must be three uppercase letters, was: '$value'",
                )
            }
            return CurrencyCode(value)
        }

        fun reconstitute(value: String): CurrencyCode = CurrencyCode(value)
    }
}
