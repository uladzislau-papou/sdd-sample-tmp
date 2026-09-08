package com.example.contractmanagement.mlc.inbound.driver

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
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.PartnerNumber
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.Percentage
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.PriceRange
import com.example.contractmanagement.mlc.core.inport.command.CreateMasterLeasingContractCommand
import com.example.contractmanagement.mlc.core.inport.result.CreateMasterLeasingContractResult
import com.example.contractmanagement.mlc.core.inport.usecase.CreateMasterLeasingContractUseCase
import com.example.contractmanagement.mlc.core.outport.MasterLeasingContractRepository
import com.example.contractmanagement.shared.outport.ClockPort
import com.example.contractmanagement.shared.outport.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service implementing UC07 — CreateMasterLeasingContract.
 *
 * Owns the transaction boundary. Orchestrates mapping, aggregate creation, persistence and
 * event hand-off — and contains **no** domain rule of its own
 * (`architecture.definition.md` § 4.4).
 *
 * That last claim is worth checking rather than trusting, because this use case has thirteen
 * validation rules and a driver is exactly where they tend to accumulate. Every one of them
 * lives in the value object or entity factory that owns it, so each is reachable from a domain
 * test. The two places that look like decisions here are not:
 *
 * - `PriceRange.of(min, max)` — the rule that the two bounds arrive together is a domain rule
 *   and lives in the domain. Calling it is a delegation.
 * - the `?.let { Percentage(it) }` mappings — these turn "absent" into "absent" and "present"
 *   into a validated value. The nullability is the command's shape (**PD-01**); the range check
 *   is `Percentage`'s. They are lambdas rather than `::Percentage` because each value object's
 *   primary constructor is private: validation lives in a companion `invoke`, so that the
 *   read path can bypass it through `reconstitute` and a tightened **PD-10** cannot make an
 *   already-signed contract unloadable.
 *
 * **Time.** `now` is read once from `ClockPort` and used for `creationTime`, `activationDate`
 * and the event's `occurredAt`, so all three agree by construction. Reading the clock three
 * times would let them differ by microseconds and make a fact disagree with its own record.
 * The command has no timestamp field to prefer instead (**PD-08**, **PD-11**).
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`.
 */
@Service
@Transactional
class CreateMasterLeasingContractDriver(
    private val masterLeasingContractRepository: MasterLeasingContractRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val clockPort: ClockPort,
) : CreateMasterLeasingContractUseCase {
    override fun create(command: CreateMasterLeasingContractCommand): CreateMasterLeasingContractResult {
        val now = clockPort.now()

        val configuration =
            ContractConfiguration.create(
                id = ContractConfigurationId.generate(),
                creditLimit = CreditLimit(command.creditLimit),
                contractType = ContractType(command.contractType),
                currency = CurrencyCode(command.currency),
                eligibleEmployees = EligibleEmployees(command.eligibleEmployees),
                salesChannel = command.salesChannel,
                groupJointLiability = command.groupJointLiability,
                returnQuotaPercentage = command.returnQuotaPercentage?.let { Percentage(it) },
                earlyClaimFeePercentage = command.earlyClaimFeePercentage?.let { Percentage(it) },
                earlyClaimWindowMonths = command.earlyClaimWindowMonths,
                noticePeriodRule = command.noticePeriodRule,
                paymentTerms = command.paymentTerms,
                priceRange = PriceRange.of(command.priceRangeMin, command.priceRangeMax),
                calculationBasis = command.calculationBasis,
                servicePackageOptions = command.servicePackageOptions,
                servicePackageVersion = command.servicePackageVersion,
                categoriesEditableInPortal = command.categoriesEditableInPortal,
            )

        val contract =
            MasterLeasingContract.create(
                id = MasterLeasingContractId.generate(),
                employerId = EmployerId(command.employerId),
                lessorId = LessorId(command.lessorId),
                partnerNumber = command.partnerNumber?.let { PartnerNumber(it) },
                owner = command.owner,
                configuration = configuration,
                now = now,
            )

        masterLeasingContractRepository.save(contract)
        contract.pullDomainEvents().forEach(domainEventPublisher::publish)

        return CreateMasterLeasingContractResult(
            masterLeasingContractId = contract.id.value.toString(),
            status = contract.status.name,
            configurationId = configuration.id.value.toString(),
            configurationVersion = configuration.version,
        )
    }
}
