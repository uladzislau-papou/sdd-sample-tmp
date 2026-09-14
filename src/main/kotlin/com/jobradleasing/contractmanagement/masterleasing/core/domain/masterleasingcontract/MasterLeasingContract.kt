package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.event.MasterLeasingContractRegistered
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.EmployerId
import com.jobradleasing.contractmanagement.shared.domain.LessorId
import com.jobradleasing.contractmanagement.shared.domain.event.DomainEvent
import java.time.Instant
import java.time.LocalDate

/**
 * The Leasing-Rahmenvertrag (LRV): the framework agreement between a Lessor and
 * an Employer under which individual bike leases are issued.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
class MasterLeasingContract private constructor(
    val contractId: MasterLeasingContractId,
    val employerId: EmployerId,
    val lessorId: LessorId,
    val partnerNumber: PartnerNumber,
    /** Null unless this is an affiliated contract for a company in the same corporate group. */
    val parentMasterLeasingContractId: MasterLeasingContractId?,
    configuration: MlcConfiguration,
    status: MasterLeasingContractStatus,
    activationDate: LocalDate?,
    cancelledDate: LocalDate?,
    cancellationReason: CancellationReason?,
) {
    var configuration: MlcConfiguration = configuration
        private set
    var status: MasterLeasingContractStatus = status
        private set

    /** Null until [activate] runs; set exactly once, never cleared. */
    var activationDate: LocalDate? = activationDate
        private set

    /** Null unless the contract has been cancelled; set exactly once, alongside [cancellationReason]. */
    var cancelledDate: LocalDate? = cancelledDate
        private set

    /** Null unless the contract has been cancelled; set exactly once, alongside [cancelledDate]. */
    var cancellationReason: CancellationReason? = cancellationReason
        private set

    private val domainEvents = mutableListOf<DomainEvent>()

    companion object {
        /**
         * Creates a new contract in `DRAFT`, at configuration version 1.
         *
         * @param parentMasterLeasingContractId the base contract, when this is an
         *   affiliated contract; null for a standalone contract
         * @param now the observed registration moment, from `ClockPort`
         * @return the new contract, with a pending `MasterLeasingContractRegistered` event
         * @throws InvalidMasterLeasingContractException if the contract would be
         *   its own parent (I-02) or the configuration is not version 1 (I-03)
         */
        fun register(
            contractId: MasterLeasingContractId,
            employerId: EmployerId,
            lessorId: LessorId,
            partnerNumber: PartnerNumber,
            parentMasterLeasingContractId: MasterLeasingContractId?,
            configuration: MlcConfiguration,
            now: Instant,
        ): MasterLeasingContract {
            if (parentMasterLeasingContractId == contractId) {
                throw InvalidMasterLeasingContractException(
                    "A master leasing contract must not be its own parent: $contractId",
                )
            }
            if (configuration.version != ConfigurationVersion(1)) {
                throw InvalidMasterLeasingContractException(
                    "The initial configuration must be version 1, was ${configuration.version.value}",
                )
            }
            val contract =
                MasterLeasingContract(
                    contractId = contractId,
                    employerId = employerId,
                    lessorId = lessorId,
                    partnerNumber = partnerNumber,
                    parentMasterLeasingContractId = parentMasterLeasingContractId,
                    configuration = configuration,
                    status = MasterLeasingContractStatus.DRAFT,
                    activationDate = null,
                    cancelledDate = null,
                    cancellationReason = null,
                )
            contract.domainEvents.add(
                MasterLeasingContractRegistered(contractId, employerId, lessorId, now),
            )
            return contract
        }
    }

    /**
     * Returns an immutable snapshot of recorded events and clears the internal
     * list. A second call returns empty.
     */
    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }
}
