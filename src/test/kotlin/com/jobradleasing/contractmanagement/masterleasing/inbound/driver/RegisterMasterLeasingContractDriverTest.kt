package com.jobradleasing.contractmanagement.masterleasing.inbound.driver

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.ContractType
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.InheritanceMode
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContractStatus
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.SalesChannel
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.event.MasterLeasingContractRegistered
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.MlcConfigurationCommand
import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.RegisterMasterLeasingContractCommand
import com.jobradleasing.contractmanagement.shared.outport.FixedClockPort
import com.jobradleasing.contractmanagement.shared.outport.RecordingDomainEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class RegisterMasterLeasingContractDriverTest {
    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val repository = RecordingMasterLeasingContractRepository()
    private val publisher = RecordingDomainEventPublisher()
    private val driver = RegisterMasterLeasingContractDriver(repository, publisher, FixedClockPort(now))

    @Test
    fun register_savesAndPublishes() {
        val command =
            RegisterMasterLeasingContractCommand(
                employerId = "employer-1",
                lessorId = "lessor-1",
                partnerNumber = "PN-0001",
                parentMasterLeasingContractId = null,
                configuration =
                    MlcConfigurationCommand(
                        contractType = ContractType.SALARY_SACRIFICE,
                        salesChannel = SalesChannel.DIRECT,
                        inheritanceMode = InheritanceMode.COPIED_ONCE,
                        currency = "EUR",
                        creditLimitAmount = BigDecimal("50000.0000"),
                        priceRangeMin = BigDecimal("500.0000"),
                        priceRangeMax = BigDecimal("3000.0000"),
                        eligibleEmployees = 100,
                        jointLiability = false,
                        returnQuotaPercentage = BigDecimal("10"),
                        noticePeriodMonths = 3,
                    ),
            )

        val result = driver.register(command)

        assertThat(result.status).isEqualTo("DRAFT")
        assertThat(result.configurationVersion).isEqualTo(1)
        assertThat(result.masterLeasingContractId).isNotBlank()

        assertThat(repository.saved).hasSize(1)
        val saved = repository.saved.single()
        assertThat(saved.status).isEqualTo(MasterLeasingContractStatus.DRAFT)
        assertThat(saved.contractId.value.toString()).isEqualTo(result.masterLeasingContractId)

        assertThat(publisher.published).hasSize(1)
        val published = publisher.published.single()
        assertThat(published).isInstanceOf(MasterLeasingContractRegistered::class.java)
        val event = published as MasterLeasingContractRegistered
        assertThat(event.contractId).isEqualTo(saved.contractId)
        assertThat(event.occurredAt).isEqualTo(now)
    }

    @Test
    fun register_throwsIllegalArgumentException_whenParentIdIsMalformed() {
        val command =
            RegisterMasterLeasingContractCommand(
                employerId = "employer-1",
                lessorId = "lessor-1",
                partnerNumber = "PN-0001",
                parentMasterLeasingContractId = "not-a-uuid",
                configuration =
                    MlcConfigurationCommand(
                        contractType = ContractType.SALARY_SACRIFICE,
                        salesChannel = SalesChannel.DIRECT,
                        inheritanceMode = InheritanceMode.COPIED_ONCE,
                        currency = "EUR",
                        creditLimitAmount = BigDecimal("50000.0000"),
                        priceRangeMin = BigDecimal("500.0000"),
                        priceRangeMax = BigDecimal("3000.0000"),
                        eligibleEmployees = 100,
                        jointLiability = false,
                        returnQuotaPercentage = BigDecimal("10"),
                        noticePeriodMonths = 3,
                    ),
            )

        assertThatThrownBy { driver.register(command) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(repository.saved).isEmpty()
        assertThat(publisher.published).isEmpty()
    }

    @Test
    fun register_throwsInvalidMasterLeasingContractException_whenCurrencyIsNotIso4217() {
        val command =
            RegisterMasterLeasingContractCommand(
                employerId = "employer-1",
                lessorId = "lessor-1",
                partnerNumber = "PN-0001",
                parentMasterLeasingContractId = null,
                configuration =
                    MlcConfigurationCommand(
                        contractType = ContractType.SALARY_SACRIFICE,
                        salesChannel = SalesChannel.DIRECT,
                        inheritanceMode = InheritanceMode.COPIED_ONCE,
                        currency = "NOT-A-CURRENCY",
                        creditLimitAmount = BigDecimal("50000.0000"),
                        priceRangeMin = BigDecimal("500.0000"),
                        priceRangeMax = BigDecimal("3000.0000"),
                        eligibleEmployees = 100,
                        jointLiability = false,
                        returnQuotaPercentage = BigDecimal("10"),
                        noticePeriodMonths = 3,
                    ),
            )

        assertThatThrownBy { driver.register(command) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)

        assertThat(repository.saved).isEmpty()
        assertThat(publisher.published).isEmpty()
    }
}
