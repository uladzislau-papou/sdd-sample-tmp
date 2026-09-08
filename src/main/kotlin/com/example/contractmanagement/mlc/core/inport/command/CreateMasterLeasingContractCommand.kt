package com.example.contractmanagement.mlc.core.inport.command

import java.math.BigDecimal

/**
 * Input for the CreateMasterLeasingContract use case (UC07).
 *
 * Carries only standard-library types — no domain value objects. Structural validation happens
 * when the driver maps this command onto domain types, so the domain remains the guard even if
 * a future caller has no adapter in front of it (`coding-style.definition.md` § 6.2). That is
 * not hypothetical here: the eventual trigger is employer onboarding (AGO), which will not be
 * a GraphQL client.
 *
 * **There is no timestamp field, and its absence is the contract.** `activationDate` and
 * `creationTime` are both the moment of creation, read from `ClockPort` by the driver
 * (**PD-08**), and `architecture.definition.md` § 8.1 forbids an external transport from
 * supplying one at all (**PD-11**). `TimestampRulesTest` enforces the adapter half.
 *
 * **Which fields are nullable is a decision of ours, not a sourced rule.** **PD-01** makes six
 * mandatory — `employerId`, `lessorId`, `contractType`, `creditLimit`, `currency`,
 * `eligibleEmployees` — and everything else optional. The data model page lists every field
 * and states no optionality anywhere. If PD-01 is overturned this signature changes, which is
 * why the spec calls it the widest-reaching of the eleven.
 *
 * The two price bounds are separate nullable fields here and one nullable `PriceRange` in the
 * domain. `PriceRange.of` owns the rule that they arrive together or not at all.
 *
 * SDD: see `documentation/ports/create-master-leasing-contract.inport.spec.md` § 2.1.
 */
@Suppress("LongParameterList")
data class CreateMasterLeasingContractCommand(
    val employerId: String,
    val lessorId: String,
    /** null when the caller does not yet hold the Odoo/Radar join key (**PD-03**). */
    val partnerNumber: String?,
    /** The owning internal **team**, not the acting user (**PD-09**). null when unassigned. */
    val owner: String?,
    val creditLimit: BigDecimal,
    val contractType: String,
    val currency: String,
    val eligibleEmployees: Int,
    /** null when the sales channel was not recorded. */
    val salesChannel: String?,
    val groupJointLiability: Boolean,
    /** null when no return quota was agreed [*Rückgabekontingent*]. */
    val returnQuotaPercentage: BigDecimal?,
    /** null when no early-claim fee was agreed. */
    val earlyClaimFeePercentage: BigDecimal?,
    /** null when no early-claim window was agreed. */
    val earlyClaimWindowMonths: Int?,
    /** null when the notice period follows no explicit rule [*Kündigungsfrist*]. */
    val noticePeriodRule: String?,
    /** null when no payment-terms code was agreed. */
    val paymentTerms: String?,
    /** null when no price band was agreed. Must be null iff [priceRangeMax] is. */
    val priceRangeMin: BigDecimal?,
    /** null when no price band was agreed. Must be null iff [priceRangeMin] is. */
    val priceRangeMax: BigDecimal?,
    /** null when no calculation basis was recorded. */
    val calculationBasis: String?,
    /** Empty when the employer offers no service tiers [*Servicepakete*]. */
    val servicePackageOptions: List<String>,
    /** null when the service-package terms are unversioned. */
    val servicePackageVersion: Int?,
    val categoriesEditableInPortal: Boolean,
)
