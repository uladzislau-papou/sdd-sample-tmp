package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * One version of a master leasing contract's terms — the source's `MLC_CONFIGURATION`.
 *
 * An **entity inside** the [MasterLeasingContract] aggregate, not an aggregate of its own: it
 * has an identity, but it is never loaded or changed independently of the contract that
 * points at it. The source's `mlc_config_id` is defined as "the current configuration
 * version", which is what makes the contract the consistency boundary and this the part.
 *
 * Deliberately **not** a `data class` (`coding-style.definition.md` § 2.1): a generated
 * `copy()` on a versioned terms record is an invitation to produce version 2 by mutating
 * version 1, which is precisely the operation that must go through the aggregate.
 *
 * Every value is immutable. Superseding terms means a *new* instance with a higher [version]
 * — there is no setter, and the use case that creates version 2 does not exist yet.
 *
 * **Two rules live in [create] rather than in a value object**, and `uc07` § 2.3 records why:
 * [earlyClaimWindowMonths] and [servicePackageVersion] are bounded `Int`s whose only
 * invariant is a range, and a one-field value object per bounded `Int` would add two types
 * carrying no meaning beyond it. Both are **PD-10**.
 *
 * `LongParameterList` is suppressed for the same reason it is on the aggregate: this
 * assembles an entire terms record in one constructor, so the count is a property of the terms
 * and not of a behaviour with too many knobs — which is what the rule is for, and what its
 * default threshold still catches everywhere else. The alternatives are a parameter object
 * nothing else uses, or setters, and setters here are forbidden outright
 * (`coding-style.definition.md` § 5.1).
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@Suppress("LongParameterList")
class ContractConfiguration private constructor(
    val id: ContractConfigurationId,
    val version: Int,
    val creditLimit: CreditLimit,
    val contractType: ContractType,
    val currency: CurrencyCode,
    val eligibleEmployees: EligibleEmployees,
    val salesChannel: String?,
    val groupJointLiability: Boolean,
    val returnQuotaPercentage: Percentage?,
    val earlyClaimFeePercentage: Percentage?,
    val earlyClaimWindowMonths: Int?,
    val noticePeriodRule: String?,
    val paymentTerms: String?,
    val priceRange: PriceRange?,
    val calculationBasis: String?,
    servicePackageOptions: List<String>,
    val servicePackageVersion: Int?,
    val categoriesEditableInPortal: Boolean,
) {
    /**
     * The service tiers employees may choose from under this contract [*Servicepakete*].
     *
     * Copied at construction, so a caller that keeps its own mutable list cannot change these
     * terms afterwards. Empty when the employer offers none — an optional field per
     * **PD-01**, expressed as an empty list rather than as null because "no options" and "not
     * specified" are the same state for a list and a nullable collection would make callers
     * handle two.
     */
    val servicePackageOptions: List<String> = servicePackageOptions.toList()

    companion object {
        private const val FIRST_VERSION = 1

        /**
         * Creates a terms version, enforcing the two § 2.3 rules that guard a plain `Int`.
         *
         * @throws InvalidMasterLeasingContractException if [earlyClaimWindowMonths] is
         *   negative or [servicePackageVersion] is below 1
         */
        @Suppress("LongParameterList")
        fun create(
            id: ContractConfigurationId,
            version: Int = FIRST_VERSION,
            creditLimit: CreditLimit,
            contractType: ContractType,
            currency: CurrencyCode,
            eligibleEmployees: EligibleEmployees,
            salesChannel: String?,
            groupJointLiability: Boolean,
            returnQuotaPercentage: Percentage?,
            earlyClaimFeePercentage: Percentage?,
            earlyClaimWindowMonths: Int?,
            noticePeriodRule: String?,
            paymentTerms: String?,
            priceRange: PriceRange?,
            calculationBasis: String?,
            servicePackageOptions: List<String>,
            servicePackageVersion: Int?,
            categoriesEditableInPortal: Boolean,
        ): ContractConfiguration {
            if (earlyClaimWindowMonths != null && earlyClaimWindowMonths < 0) {
                throw InvalidMasterLeasingContractException(
                    "earlyClaimWindowMonths must not be negative, was: $earlyClaimWindowMonths",
                )
            }
            if (servicePackageVersion != null && servicePackageVersion < FIRST_VERSION) {
                throw InvalidMasterLeasingContractException(
                    "servicePackageVersion must be at least $FIRST_VERSION, was: $servicePackageVersion",
                )
            }

            return ContractConfiguration(
                id = id,
                version = version,
                creditLimit = creditLimit,
                contractType = contractType,
                currency = currency,
                eligibleEmployees = eligibleEmployees,
                salesChannel = salesChannel,
                groupJointLiability = groupJointLiability,
                returnQuotaPercentage = returnQuotaPercentage,
                earlyClaimFeePercentage = earlyClaimFeePercentage,
                earlyClaimWindowMonths = earlyClaimWindowMonths,
                noticePeriodRule = noticePeriodRule,
                paymentTerms = paymentTerms,
                priceRange = priceRange,
                calculationBasis = calculationBasis,
                servicePackageOptions = servicePackageOptions,
                servicePackageVersion = servicePackageVersion,
                categoriesEditableInPortal = categoriesEditableInPortal,
            )
        }

        /**
         * Rebuilds a terms version from its persisted state, re-checking no invariant.
         *
         * The row was valid when it was written. Re-validating a past fact means a change to
         * **PD-10** — which is provisional and expected to change — can make history
         * unreadable, and the terms of a signed contract are exactly the thing that must not
         * become unloadable because we tightened a rule afterwards.
         *
         * `ClassRoleRulesTest` restricts the caller to `..outbound.persistence..`.
         */
        @Suppress("LongParameterList")
        fun reconstitute(
            id: ContractConfigurationId,
            version: Int,
            creditLimit: CreditLimit,
            contractType: ContractType,
            currency: CurrencyCode,
            eligibleEmployees: EligibleEmployees,
            salesChannel: String?,
            groupJointLiability: Boolean,
            returnQuotaPercentage: Percentage?,
            earlyClaimFeePercentage: Percentage?,
            earlyClaimWindowMonths: Int?,
            noticePeriodRule: String?,
            paymentTerms: String?,
            priceRange: PriceRange?,
            calculationBasis: String?,
            servicePackageOptions: List<String>,
            servicePackageVersion: Int?,
            categoriesEditableInPortal: Boolean,
        ): ContractConfiguration =
            ContractConfiguration(
                id = id,
                version = version,
                creditLimit = creditLimit,
                contractType = contractType,
                currency = currency,
                eligibleEmployees = eligibleEmployees,
                salesChannel = salesChannel,
                groupJointLiability = groupJointLiability,
                returnQuotaPercentage = returnQuotaPercentage,
                earlyClaimFeePercentage = earlyClaimFeePercentage,
                earlyClaimWindowMonths = earlyClaimWindowMonths,
                noticePeriodRule = noticePeriodRule,
                paymentTerms = paymentTerms,
                priceRange = priceRange,
                calculationBasis = calculationBasis,
                servicePackageOptions = servicePackageOptions,
                servicePackageVersion = servicePackageVersion,
                categoriesEditableInPortal = categoriesEditableInPortal,
            )
    }
}
