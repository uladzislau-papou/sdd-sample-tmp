package com.example.contractmanagement.mlc.inbound.graphql

import java.math.BigDecimal

/**
 * GraphQL input for UC07 — CreateMasterLeasingContract.
 *
 * **The monetary and percentage fields are `String`, and that is a schema fact rather than a
 * preference.** This service declares no decimal scalar, and GraphQL's built-in `Float` is a
 * double-precision binary float — which cannot represent a credit limit exactly. The booking
 * example carries its date as a `String!` for the same reason. So the wire format is decimal
 * notation and this type parses it, mirroring `RequestTourBookingInput.tourDateAsLocalDate`.
 *
 * **There is no timestamp field, and its absence is the contract** (**PD-11**).
 * `architecture.definition.md` § 8.1 puts a GraphQL client in the same category as a REST
 * client: the time an action happened is the system's observation, not the caller's claim.
 * `TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes` fails if one is added.
 *
 * **Nullable with a default, not non-null**, for the two `Boolean`s and the list: GraphQL
 * leaves an unsupplied field null, and `coding-style.definition.md` § 1.4 requires an adapter
 * to normalize an incoming null immediately so nothing nullable travels inward unless the
 * domain says absence is meaningful. For these three, absence is not meaningful — **PD-01**
 * gives them defaults — so the default is applied here and the command sees a real value.
 *
 * The required fields are exactly PD-01's six, which is a decision of ours and not a sourced
 * rule.
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 9.
 */
@Suppress("LongParameterList")
data class CreateMasterLeasingContractInput(
    val employerId: String,
    val lessorId: String,
    val partnerNumber: String? = null,
    val owner: String? = null,
    /** Decimal notation, e.g. `50000.00`. */
    val creditLimit: String,
    val contractType: String,
    /** ISO-4217 alphabetic code, e.g. `EUR`. */
    val currency: String,
    val eligibleEmployees: Int,
    val salesChannel: String? = null,
    val groupJointLiability: Boolean? = null,
    /** Decimal notation between `0` and `100`. */
    val returnQuotaPercentage: String? = null,
    /** Decimal notation between `0` and `100`. */
    val earlyClaimFeePercentage: String? = null,
    val earlyClaimWindowMonths: Int? = null,
    val noticePeriodRule: String? = null,
    val paymentTerms: String? = null,
    /** Decimal notation. Supply together with [priceRangeMax] or not at all. */
    val priceRangeMin: String? = null,
    /** Decimal notation. Supply together with [priceRangeMin] or not at all. */
    val priceRangeMax: String? = null,
    val calculationBasis: String? = null,
    val servicePackageOptions: List<String>? = null,
    val servicePackageVersion: Int? = null,
    val categoriesEditableInPortal: Boolean? = null,
) {
    fun creditLimitAsBigDecimal(): BigDecimal = decimal(creditLimit, "creditLimit")

    fun returnQuotaPercentageAsBigDecimal(): BigDecimal? =
        returnQuotaPercentage?.let { decimal(it, "returnQuotaPercentage") }

    fun earlyClaimFeePercentageAsBigDecimal(): BigDecimal? =
        earlyClaimFeePercentage?.let { decimal(it, "earlyClaimFeePercentage") }

    fun priceRangeMinAsBigDecimal(): BigDecimal? = priceRangeMin?.let { decimal(it, "priceRangeMin") }

    fun priceRangeMaxAsBigDecimal(): BigDecimal? = priceRangeMax?.let { decimal(it, "priceRangeMax") }

    /**
     * Parses decimal notation, naming the field when it fails.
     *
     * Throws [IllegalArgumentException] and **not** a domain exception, deliberately. The
     * taxonomy in `coding-style.definition.md` § 6.2 keys on where a value comes from, and
     * this one never becomes a domain type at all — it is malformed at the wire format, the
     * same class of failure as an identifier that is not a UUID. § 6.3 records that
     * `IllegalArgumentException → 400` is mapped in every exception resolver as the backstop
     * for exactly this.
     *
     * The field name is in the message because `NumberFormatException`'s own message quotes
     * the value without saying which of five decimal fields carried it.
     */
    private fun decimal(
        raw: String,
        field: String,
    ): BigDecimal =
        try {
            BigDecimal(raw)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("$field must be decimal notation, was: '$raw'", e)
        }
}
