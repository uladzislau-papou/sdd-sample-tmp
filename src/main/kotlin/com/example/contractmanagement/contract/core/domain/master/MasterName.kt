package com.example.contractmanagement.contract.core.domain.master

import com.example.contractmanagement.contract.core.domain.master.exception.InvalidMasterException

/**
 * The name of a [Master].
 *
 * Invariant I-01: non-blank after trimming, and at most [MAX_LENGTH] characters.
 *
 * Trimming qualifies the blank check and nothing else — the value is stored as supplied.
 * I-01 states the rule on the trimmed value without saying the name is canonicalised, and
 * normalising here would be behaviour no specification asked for.
 *
 * A `data class` rather than a `value class`: it validates, and `coding-style.definition.md`
 * § 2.2 prefers a plain data class in that case.
 *
 * SDD: see `documentation/domain/aggregate-master.spec.md` § 2 (I-01) and
 * `documentation/use-cases/uc01-create-master.spec.md` § 7 (AC-03).
 */
data class MasterName(
    val value: String,
) {
    init {
        if (value.trim().isEmpty()) {
            throw InvalidMasterException("A master name must not be blank")
        }
        if (value.length > MAX_LENGTH) {
            throw InvalidMasterException(
                "A master name must be at most $MAX_LENGTH characters, but was ${value.length}",
            )
        }
    }

    companion object {
        /** I-01's upper bound, inclusive. */
        const val MAX_LENGTH: Int = 200
    }
}
