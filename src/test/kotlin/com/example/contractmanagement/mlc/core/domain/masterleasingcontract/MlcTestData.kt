package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import java.math.BigDecimal

/**
 * Test data builders for the `mlc` domain.
 *
 * `test.definition.md` § 4.2 governs the shape: a builder supplies a valid default for every
 * field and lets one test override the field it is about. That matters more here than usual —
 * `ContractConfiguration` carries seventeen fields, of which **PD-01** makes four mandatory,
 * so a test asserting one rule would otherwise restate thirteen irrelevant values and the
 * rule under test would be invisible in the noise.
 *
 * [minimalConfiguration] is not a convenience: it is the direct subject of **AC-08**, which
 * asserts that a command carrying only PD-01's mandatory fields succeeds. If PD-01 is
 * overturned toward more mandatory fields, this function stops compiling — which is the
 * earliest possible warning and the reason it is a named function rather than an inline
 * default.
 */
object MlcTestData {
    /**
     * `LongParameterList` is suppressed for the same reason it is on the aggregate and the row
     * types: this assembles an entire terms record, so the count is a property of the record
     * and not of a behaviour with too many knobs — which is what the rule is for, and what its
     * default threshold of five still catches everywhere else.
     *
     * The alternative here is worse than usual. `test.definition.md` § 4.2 asks a builder to
     * default every field and let one test override the one it is about; collapsing these into
     * a parameter object would mean every call site constructs that object in full, which is
     * precisely the restating-thirteen-irrelevant-values noise the builder exists to remove.
     */
    @Suppress("LongParameterList")
    fun configuration(
        id: ContractConfigurationId = ContractConfigurationId.generate(),
        version: Int = 1,
        creditLimit: BigDecimal = BigDecimal("50000"),
        contractType: String = "salary-sacrifice-leasing",
        currency: String = "EUR",
        eligibleEmployees: Int = 250,
        salesChannel: String? = "direct",
        groupJointLiability: Boolean = false,
        returnQuotaPercentage: BigDecimal? = BigDecimal("10"),
        earlyClaimFeePercentage: BigDecimal? = BigDecimal("2.5"),
        earlyClaimWindowMonths: Int? = 12,
        noticePeriodRule: String? = "3-months-to-quarter-end",
        paymentTerms: String? = "NET30",
        priceRangeMin: BigDecimal? = BigDecimal("749"),
        priceRangeMax: BigDecimal? = BigDecimal("11000"),
        calculationBasis: String? = "sale-price-plus-shipping",
        servicePackageOptions: List<String> = listOf("basic", "comfort"),
        servicePackageVersion: Int? = 3,
        categoriesEditableInPortal: Boolean = true,
    ): ContractConfiguration =
        ContractConfiguration.create(
            id = id,
            version = version,
            creditLimit = CreditLimit(creditLimit),
            contractType = ContractType(contractType),
            currency = CurrencyCode(currency),
            eligibleEmployees = EligibleEmployees(eligibleEmployees),
            salesChannel = salesChannel,
            groupJointLiability = groupJointLiability,
            returnQuotaPercentage = returnQuotaPercentage?.let { Percentage(it) },
            earlyClaimFeePercentage = earlyClaimFeePercentage?.let { Percentage(it) },
            earlyClaimWindowMonths = earlyClaimWindowMonths,
            noticePeriodRule = noticePeriodRule,
            paymentTerms = paymentTerms,
            priceRange = PriceRange.of(priceRangeMin, priceRangeMax),
            calculationBasis = calculationBasis,
            servicePackageOptions = servicePackageOptions,
            servicePackageVersion = servicePackageVersion,
            categoriesEditableInPortal = categoriesEditableInPortal,
        )

    /**
     * A configuration carrying only the four terms **PD-01** makes mandatory.
     *
     * The subject of AC-08. Every optional field is absent, which is also what
     * `MasterLeasingContractJpaRepositoryIT.save_persistsOptionalTermsAsNull_whenAbsent`
     * roundtrips.
     */
    fun minimalConfiguration(): ContractConfiguration =
        configuration(
            salesChannel = null,
            returnQuotaPercentage = null,
            earlyClaimFeePercentage = null,
            earlyClaimWindowMonths = null,
            noticePeriodRule = null,
            paymentTerms = null,
            priceRangeMin = null,
            priceRangeMax = null,
            calculationBasis = null,
            servicePackageOptions = emptyList(),
            servicePackageVersion = null,
        )
}
