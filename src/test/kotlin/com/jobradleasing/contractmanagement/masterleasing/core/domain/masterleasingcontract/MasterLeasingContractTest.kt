package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.event.MasterLeasingContractRegistered
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.EmployerId
import com.jobradleasing.contractmanagement.shared.domain.LessorId
import com.jobradleasing.contractmanagement.shared.domain.Money
import com.jobradleasing.contractmanagement.shared.domain.Percentage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MasterLeasingContractTest {
    private fun aValidConfiguration(version: Int = 1): MlcConfiguration =
        MlcConfiguration(
            version = ConfigurationVersion(version),
            inheritanceMode = InheritanceMode.COPIED_ONCE,
            contractType = ContractType.SALARY_SACRIFICE,
            salesChannel = SalesChannel.DIRECT,
            creditLimit = CreditLimit(Money.euro("50000.0000")),
            priceRange = PriceRange(Money.euro("500.0000"), Money.euro("3000.0000")),
            eligibleEmployees = EligibleEmployees(100),
            jointLiability = false,
            returnQuota = ReturnQuota(Percentage.of("10")),
            noticePeriod = NoticePeriod(3),
        )

    @Test
    fun register_createsContractInDraft() {
        val contractId = MasterLeasingContractId(UUID.randomUUID())
        val employerId = EmployerId("employer-1")
        val lessorId = LessorId("lessor-1")
        val partnerNumber = PartnerNumber("PN-0001")
        val configuration = aValidConfiguration()
        val now = Instant.parse("2026-01-15T10:00:00Z")

        val contract =
            MasterLeasingContract.register(
                contractId = contractId,
                employerId = employerId,
                lessorId = lessorId,
                partnerNumber = partnerNumber,
                parentMasterLeasingContractId = null,
                configuration = configuration,
                now = now,
            )

        assertThat(contract.contractId).isEqualTo(contractId)
        assertThat(contract.status).isEqualTo(MasterLeasingContractStatus.DRAFT)
        assertThat(contract.configuration.version).isEqualTo(ConfigurationVersion(1))
        assertThat(contract.activationDate).isNull()
        assertThat(contract.cancelledDate).isNull()
        assertThat(contract.cancellationReason).isNull()
        assertThat(contract.parentMasterLeasingContractId).isNull()

        val events = contract.pullDomainEvents()
        assertThat(events).containsExactly(
            MasterLeasingContractRegistered(contractId, employerId, lessorId, now),
        )
        assertThat(contract.pullDomainEvents()).isEmpty()
    }

    @Test
    fun register_storesParentReference() {
        val contractId = MasterLeasingContractId(UUID.randomUUID())
        val parentId = MasterLeasingContractId(UUID.randomUUID())

        val contract =
            MasterLeasingContract.register(
                contractId = contractId,
                employerId = EmployerId("employer-1"),
                lessorId = LessorId("lessor-1"),
                partnerNumber = PartnerNumber("PN-0001"),
                parentMasterLeasingContractId = parentId,
                configuration = aValidConfiguration(),
                now = Instant.parse("2026-01-15T10:00:00Z"),
            )

        assertThat(contract.parentMasterLeasingContractId).isEqualTo(parentId)
    }

    @Test
    fun register_throwsInvalidMasterLeasingContractException_whenParentIsSelf() {
        val contractId = MasterLeasingContractId(UUID.randomUUID())

        assertThatThrownBy {
            MasterLeasingContract.register(
                contractId = contractId,
                employerId = EmployerId("employer-1"),
                lessorId = LessorId("lessor-1"),
                partnerNumber = PartnerNumber("PN-0001"),
                parentMasterLeasingContractId = contractId,
                configuration = aValidConfiguration(),
                now = Instant.parse("2026-01-15T10:00:00Z"),
            )
        }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun register_throwsInvalidMasterLeasingContractException_whenInitialVersionIsNotOne() {
        assertThatThrownBy {
            MasterLeasingContract.register(
                contractId = MasterLeasingContractId(UUID.randomUUID()),
                employerId = EmployerId("employer-1"),
                lessorId = LessorId("lessor-1"),
                partnerNumber = PartnerNumber("PN-0001"),
                parentMasterLeasingContractId = null,
                configuration = aValidConfiguration(version = 2),
                now = Instant.parse("2026-01-15T10:00:00Z"),
            )
        }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
