package com.example.contractmanagement.mlc.outbound.persistence

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.ContractConfiguration
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.ContractConfigurationId
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.ContractType
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.CreditLimit
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.CurrencyCode
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.EligibleEmployees
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.EmployerId
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.LessorId
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContract
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractId
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractStatus
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.PartnerNumber
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.Percentage
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.PriceRange
import java.util.UUID

/**
 * Pure mapping between the [MasterLeasingContract] aggregate and its two row types.
 *
 * No Spring, no IO. Reconstitution goes through `MasterLeasingContract.reconstitute` and
 * `ContractConfiguration.reconstitute`, which is why `ClassRoleRulesTest` permits those calls
 * from `..outbound.persistence..` and from nowhere else: any other caller would be skipping
 * the invariants the factories enforce.
 *
 * **The read path validates nothing, and getting that right took a correction.** The value
 * objects this rebuilds enforce **PD-10** — a provisional validation matrix of ours, expected
 * to be replaced when the JCM project builds the real one. Re-validating on read would mean
 * tightening a rule makes previously written contracts unloadable, and the terms of a signed
 * agreement are exactly the thing that must not become unreadable because we changed our mind.
 *
 * The first version of this class **claimed that property and did not deliver it**, which
 * `ddd-hex-reviewer` caught. Calling `MasterLeasingContract.reconstitute` is not sufficient:
 * those factories re-check nothing, but they receive *already-constructed* value objects, and
 * this mapper is what constructs them — through validating constructors. The claim went
 * unfalsified in both directions because the integration test only round-tripped values that
 * satisfy the current rules.
 *
 * Fixed structurally rather than by softening the claim: each value object's primary
 * constructor is now private, validation lives in a companion `invoke`, and the read path calls
 * `reconstitute`. `@ConsistentCopyVisibility` makes the generated `copy()` private too, so the
 * validating path cannot be bypassed by any caller — which closes a hole the original
 * public-constructor version had and nothing had noticed.
 *
 * [readPriceRange] handles the one place the shapes genuinely differ: two nullable columns, one
 * nullable value object. Its KDoc records why the pairing sits here and not on `PriceRange`.
 */
internal class MasterLeasingContractMapper {
    fun toContractEntity(contract: MasterLeasingContract) =
        MasterLeasingContractJpaEntity(
            id = contract.id.value.toString(),
            employerId = contract.employerId.value,
            lessorId = contract.lessorId.value,
            partnerNumber = contract.partnerNumber?.value,
            owner = contract.owner,
            status = contract.status.name,
            creationTime = contract.creationTime,
            activationDate = contract.activationDate,
            mlcConfigId = contract.currentConfiguration.id.value.toString(),
        )

    fun toConfigurationEntity(configuration: ContractConfiguration) =
        ContractConfigurationJpaEntity(
            id = configuration.id.value.toString(),
            version = configuration.version,
            creditLimit = configuration.creditLimit.value,
            contractType = configuration.contractType.value,
            currency = configuration.currency.value,
            eligibleEmployees = configuration.eligibleEmployees.value,
            salesChannel = configuration.salesChannel,
            kuvJointLiability = configuration.groupJointLiability,
            returnQuotaPercentage = configuration.returnQuotaPercentage?.value,
            earlyClaimFeePercentage = configuration.earlyClaimFeePercentage?.value,
            earlyClaimWindowMonths = configuration.earlyClaimWindowMonths,
            noticePeriodRule = configuration.noticePeriodRule,
            paymentTerms = configuration.paymentTerms,
            priceRangeMin = configuration.priceRange?.min,
            priceRangeMax = configuration.priceRange?.max,
            calculationBasis = configuration.calculationBasis,
            servicePackageOptions = configuration.servicePackageOptions.toMutableList(),
            servicePackageVersion = configuration.servicePackageVersion,
            categoriesEditableInPortal = configuration.categoriesEditableInPortal,
        )

    fun toDomain(
        contract: MasterLeasingContractJpaEntity,
        configuration: ContractConfigurationJpaEntity,
    ): MasterLeasingContract =
        MasterLeasingContract.reconstitute(
            id = MasterLeasingContractId.of(UUID.fromString(contract.id)),
            employerId = EmployerId.reconstitute(contract.employerId),
            lessorId = LessorId.reconstitute(contract.lessorId),
            partnerNumber = contract.partnerNumber?.let(PartnerNumber::reconstitute),
            owner = contract.owner,
            status = MasterLeasingContractStatus.valueOf(contract.status),
            creationTime = contract.creationTime,
            activationDate = contract.activationDate,
            currentConfiguration = toDomain(configuration),
        )

    private fun toDomain(entity: ContractConfigurationJpaEntity): ContractConfiguration =
        ContractConfiguration.reconstitute(
            id = ContractConfigurationId.of(UUID.fromString(entity.id)),
            version = entity.version,
            creditLimit = CreditLimit.reconstitute(entity.creditLimit),
            contractType = ContractType.reconstitute(entity.contractType),
            currency = CurrencyCode.reconstitute(entity.currency),
            eligibleEmployees = EligibleEmployees.reconstitute(entity.eligibleEmployees),
            salesChannel = entity.salesChannel,
            groupJointLiability = entity.kuvJointLiability,
            returnQuotaPercentage = entity.returnQuotaPercentage?.let(Percentage::reconstitute),
            earlyClaimFeePercentage = entity.earlyClaimFeePercentage?.let(Percentage::reconstitute),
            earlyClaimWindowMonths = entity.earlyClaimWindowMonths,
            noticePeriodRule = entity.noticePeriodRule,
            paymentTerms = entity.paymentTerms,
            priceRange = readPriceRange(entity),
            calculationBasis = entity.calculationBasis,
            servicePackageOptions = entity.servicePackageOptions.toList(),
            servicePackageVersion = entity.servicePackageVersion,
            categoriesEditableInPortal = entity.categoriesEditableInPortal,
        )

    /**
     * Pairs the two nullable price columns into an optional range, **without validating**.
     *
     * The pairing lives here rather than as a `PriceRange.reconstituteOptional` factory, and
     * the placement was forced by a rule rather than chosen. `ClassRoleRulesTest` matches any
     * method named exactly `reconstitute` in `core.domain` and forbids a caller outside
     * `..outbound.persistence..` — so a companion helper calling `reconstitute` from inside
     * `core.domain` failed that rule, while naming it `reconstituteOptional` would have escaped
     * the rule's exact-name match and left the read path unguarded. Neither is acceptable.
     * Doing the pairing here keeps the guarded name at its only call site.
     *
     * A row carrying exactly one bound is unreachable through `save`, so it is read as absent
     * rather than thrown on: a read path that can throw on stored data is a read path that can
     * make history unreadable.
     */
    private fun readPriceRange(entity: ContractConfigurationJpaEntity): PriceRange? {
        val min = entity.priceRangeMin
        val max = entity.priceRangeMax
        return if (min != null && max != null) PriceRange.reconstitute(min, max) else null
    }
}
