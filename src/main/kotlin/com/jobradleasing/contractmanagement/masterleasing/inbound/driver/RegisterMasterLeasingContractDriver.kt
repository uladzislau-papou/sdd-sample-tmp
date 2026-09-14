package com.jobradleasing.contractmanagement.masterleasing.inbound.driver

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.ConfigurationVersion
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.CreditLimit
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.EligibleEmployees
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContract
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContractId
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MlcConfiguration
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.NoticePeriod
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.PartnerNumber
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.PriceRange
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.ReturnQuota
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.RegisterMasterLeasingContractCommand
import com.jobradleasing.contractmanagement.masterleasing.core.inport.result.RegisterMasterLeasingContractResult
import com.jobradleasing.contractmanagement.masterleasing.core.inport.usecase.RegisterMasterLeasingContractUseCase
import com.jobradleasing.contractmanagement.masterleasing.core.outport.MasterLeasingContractRepository
import com.jobradleasing.contractmanagement.shared.domain.EmployerId
import com.jobradleasing.contractmanagement.shared.domain.LessorId
import com.jobradleasing.contractmanagement.shared.domain.Money
import com.jobradleasing.contractmanagement.shared.domain.Percentage
import com.jobradleasing.contractmanagement.shared.outport.ClockPort
import com.jobradleasing.contractmanagement.shared.outport.DomainEventPublisher
import java.util.Currency
import java.util.UUID

/**
 * Use case implementation for UC01 — RegisterMasterLeasingContract. Builds
 * every value object before constructing the aggregate, so an invalid credit
 * limit is a `BAD_REQUEST` with no database round trip.
 *
 * SDD: See `documentation/ports/register-master-leasing-contract.inport.spec.md` § 4.
 */
class RegisterMasterLeasingContractDriver(
    private val repository: MasterLeasingContractRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val clockPort: ClockPort,
) : RegisterMasterLeasingContractUseCase {
    override fun register(command: RegisterMasterLeasingContractCommand): RegisterMasterLeasingContractResult {
        val contractId = MasterLeasingContractId(UUID.randomUUID())
        val employerId = EmployerId(command.employerId)
        val lessorId = LessorId(command.lessorId)
        val partnerNumber = PartnerNumber(command.partnerNumber)
        val parentMasterLeasingContractId =
            command.parentMasterLeasingContractId?.let { MasterLeasingContractId(UUID.fromString(it)) }

        val currency =
            try {
                Currency.getInstance(command.configuration.currency)
            } catch (e: IllegalArgumentException) {
                throw InvalidMasterLeasingContractException(
                    "currency is not a valid ISO-4217 code: ${command.configuration.currency}",
                    e,
                )
            }
        val configuration =
            MlcConfiguration(
                version = ConfigurationVersion(1),
                inheritanceMode = command.configuration.inheritanceMode,
                contractType = command.configuration.contractType,
                salesChannel = command.configuration.salesChannel,
                creditLimit = CreditLimit(Money.of(command.configuration.creditLimitAmount, currency)),
                priceRange =
                    PriceRange(
                        min = Money.of(command.configuration.priceRangeMin, currency),
                        max = Money.of(command.configuration.priceRangeMax, currency),
                    ),
                eligibleEmployees = EligibleEmployees(command.configuration.eligibleEmployees),
                jointLiability = command.configuration.jointLiability,
                returnQuota = ReturnQuota(Percentage.of(command.configuration.returnQuotaPercentage)),
                noticePeriod = NoticePeriod(command.configuration.noticePeriodMonths),
            )

        val now = clockPort.now()
        val contract =
            MasterLeasingContract.register(
                contractId = contractId,
                employerId = employerId,
                lessorId = lessorId,
                partnerNumber = partnerNumber,
                parentMasterLeasingContractId = parentMasterLeasingContractId,
                configuration = configuration,
                now = now,
            )

        repository.save(contract)
        contract.pullDomainEvents().forEach(domainEventPublisher::publish)

        return RegisterMasterLeasingContractResult(
            masterLeasingContractId = contractId.value.toString(),
            status = contract.status.name,
            configurationVersion = contract.configuration.version.value,
        )
    }
}
