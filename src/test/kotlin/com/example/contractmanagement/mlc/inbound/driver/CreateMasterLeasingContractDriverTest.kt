package com.example.contractmanagement.mlc.inbound.driver

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractStatus
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.event.MasterLeasingContractCreated
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.example.contractmanagement.mlc.core.inport.command.CreateMasterLeasingContractCommand
import com.example.contractmanagement.mlc.core.outport.MasterLeasingContractRepository
import com.example.contractmanagement.shared.outport.ClockPort
import com.example.contractmanagement.shared.outport.DomainEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

/**
 * Use case tests for [CreateMasterLeasingContractDriver] (UC07).
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`. Each test
 * names the `AC-NN` it covers, per `test.definition.md` § 9.
 *
 * **Four of the eight criteria rest on a provisional decision of ours**, not on a source:
 * AC-05 on PD-07, AC-06 on PD-08 and PD-11, AC-07 on PD-10, AC-08 on PD-01. If one of those
 * decisions is overturned by an answer from the JCM project, the test that fails is the one
 * to read first — which is why the `PD` numbers are here and not only in the spec.
 *
 * Observation is at the repository and the publisher, both mocked. § 3 returns identifiers
 * only and this service has no read side, so the aggregate handed to the outbound port is the
 * only place the terms are observable — that is what AC-01 and AC-02 mean by "handed to".
 */
class CreateMasterLeasingContractDriverTest {
    private val now: Instant = Instant.parse("2026-09-08T09:30:00Z")

    private val repository: MasterLeasingContractRepository = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val clockPort: ClockPort = mock { on { now() } doReturn now }

    private val driver = CreateMasterLeasingContractDriver(repository, eventPublisher, clockPort)

    /** A command carrying every field, mandatory and optional alike. */
    private val command =
        CreateMasterLeasingContractCommand(
            employerId = "emp-1",
            lessorId = "lessor-1",
            partnerNumber = "p-4711",
            owner = "contract-operations",
            creditLimit = BigDecimal("50000"),
            contractType = "salary-sacrifice-leasing",
            currency = "EUR",
            eligibleEmployees = 250,
            salesChannel = "direct",
            groupJointLiability = true,
            returnQuotaPercentage = BigDecimal("10"),
            earlyClaimFeePercentage = BigDecimal("2.5"),
            earlyClaimWindowMonths = 12,
            noticePeriodRule = "3-months-to-quarter-end",
            paymentTerms = "NET30",
            priceRangeMin = BigDecimal("749"),
            priceRangeMax = BigDecimal("11000"),
            calculationBasis = "sale-price-plus-shipping",
            servicePackageOptions = listOf("basic", "comfort"),
            servicePackageVersion = 3,
            categoriesEditableInPortal = true,
        )

    /** A command carrying only the six fields **PD-01** makes mandatory. The subject of AC-08. */
    private val minimalCommand =
        CreateMasterLeasingContractCommand(
            employerId = "emp-1",
            lessorId = "lessor-1",
            partnerNumber = null,
            owner = null,
            creditLimit = BigDecimal("50000"),
            contractType = "salary-sacrifice-leasing",
            currency = "EUR",
            eligibleEmployees = 250,
            salesChannel = null,
            groupJointLiability = false,
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
            categoriesEditableInPortal = false,
        )

    /** AC-01 — and § 3's claim that the payload carries the configuration's identity. */
    @Test
    fun create_returnsIdentifiersAndStatus() {
        val result = driver.create(command)

        assertThat(result.masterLeasingContractId).isNotBlank()
        assertThat(result.configurationId).isNotBlank()
        assertThat(result.configurationVersion).isEqualTo(1)
        assertThat(result.status).isEqualTo(MasterLeasingContractStatus.ACTIVE.name)
    }

    /** AC-01 — exactly one current configuration, at version 1. */
    @Test
    fun create_savesAggregateWithExactlyOneCurrentConfiguration() {
        driver.create(command)

        verify(repository).save(
            check {
                assertThat(it.currentConfiguration.version).isEqualTo(1)
                assertThat(it.currentConfiguration.id.value).isNotNull()
            },
        )
    }

    /** AC-02 — every § 2.2 term reaches the aggregate unchanged. */
    @Test
    fun create_savesTermsUnchanged() {
        driver.create(command)

        verify(repository).save(
            check { contract ->
                assertThat(contract.employerId.value).isEqualTo("emp-1")
                assertThat(contract.lessorId.value).isEqualTo("lessor-1")
                assertThat(contract.partnerNumber?.value).isEqualTo("p-4711")
                assertThat(contract.owner).isEqualTo("contract-operations")

                val terms = contract.currentConfiguration
                assertThat(terms.creditLimit.value).isEqualByComparingTo(BigDecimal("50000"))
                assertThat(terms.contractType.value).isEqualTo("salary-sacrifice-leasing")
                assertThat(terms.currency.value).isEqualTo("EUR")
                assertThat(terms.eligibleEmployees.value).isEqualTo(250)
                assertThat(terms.salesChannel).isEqualTo("direct")
                assertThat(terms.groupJointLiability).isTrue()
                assertThat(terms.returnQuotaPercentage?.value).isEqualByComparingTo(BigDecimal("10"))
                assertThat(terms.earlyClaimFeePercentage?.value).isEqualByComparingTo(BigDecimal("2.5"))
                assertThat(terms.earlyClaimWindowMonths).isEqualTo(12)
                assertThat(terms.noticePeriodRule).isEqualTo("3-months-to-quarter-end")
                assertThat(terms.paymentTerms).isEqualTo("NET30")
                assertThat(terms.priceRange?.min).isEqualByComparingTo(BigDecimal("749"))
                assertThat(terms.priceRange?.max).isEqualByComparingTo(BigDecimal("11000"))
                assertThat(terms.calculationBasis).isEqualTo("sale-price-plus-shipping")
                assertThat(terms.servicePackageOptions).containsExactly("basic", "comfort")
                assertThat(terms.servicePackageVersion).isEqualTo(3)
                assertThat(terms.categoriesEditableInPortal).isTrue()
            },
        )
    }

    /** AC-03 — the one rule argued from the sources rather than decided. */
    @Test
    fun create_throwsInvalidMasterLeasingContractException_whenEmployerIdBlank() {
        assertThatThrownBy { driver.create(command.copy(employerId = " ")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)

        verify(repository, never()).save(any())
        verify(eventPublisher, never()).publish(any())
    }

    /** AC-04 — cardinality and payload, nothing about delivery. */
    @Test
    fun create_publishesExactlyOneMasterLeasingContractCreated() {
        val result = driver.create(command)

        verify(eventPublisher).publish(
            check<MasterLeasingContractCreated> {
                assertThat(it.masterLeasingContractId.value.toString())
                    .isEqualTo(result.masterLeasingContractId)
                assertThat(it.occurredAt).isEqualTo(now)
            },
        )
    }

    /** AC-05 — rests on **PD-07**. */
    @Test
    fun create_savesStatusActive() {
        driver.create(command)

        verify(repository).save(
            check { assertThat(it.status).isEqualTo(MasterLeasingContractStatus.ACTIVE) },
        )
    }

    /**
     * AC-06 — rests on **PD-08** and **PD-11**: the system dates the contract, not the caller.
     *
     * The complementary half — that no timestamp can reach the input at all — is structural
     * and is asserted by `TimestampRulesTest` over the adapter packages. A use-case test
     * cannot see it, because a command with no timestamp field has nothing to assert about.
     */
    @Test
    fun create_usesClockPort_forCreationTimeAndActivationDate() {
        driver.create(command)

        verify(repository).save(
            check {
                assertThat(it.creationTime).isEqualTo(now)
                assertThat(it.activationDate).isEqualTo(now)
            },
        )
    }

    /** AC-07 — rests on **PD-10**. One named rule, not "input is validated". */
    @Test
    fun create_throwsInvalidMasterLeasingContractException_whenPriceRangeMinExceedsMax() {
        val invalid = command.copy(priceRangeMin = BigDecimal("11000"), priceRangeMax = BigDecimal("749"))

        assertThatThrownBy { driver.create(invalid) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)

        verify(repository, never()).save(any())
        verify(eventPublisher, never()).publish(any())
    }

    /** AC-08 — rests on **PD-01**. Fails first if PD-01 gains a mandatory field. */
    @Test
    fun create_succeeds_whenOptionalTermsAbsent() {
        val result = driver.create(minimalCommand)

        assertThat(result.status).isEqualTo(MasterLeasingContractStatus.ACTIVE.name)
        verify(repository).save(
            check {
                assertThat(it.partnerNumber).isNull()
                assertThat(it.owner).isNull()
                assertThat(it.currentConfiguration.priceRange).isNull()
                assertThat(it.currentConfiguration.servicePackageOptions).isEmpty()
            },
        )
        verify(eventPublisher).publish(any())
    }

    /**
     * § 8's third bullet, the half that is testable here: **nothing is dispatched.**
     *
     * Added because `conformance-reviewer` found that § 8 specified *"the transaction rolls back
     * and no contract exists … nothing is dispatched"* and **no test anywhere** exercised it —
     * not at this layer, not at the repository. The classification half now lives in
     * `MlcGraphQLExceptionResolverTest`; this is the ordering half.
     *
     * It is cheap and worth having because the ordering in the driver is load-bearing under
     * `adr/0022`: `save` precedes `publish`, so a persistence failure must prevent publication
     * rather than leave an event describing a contract that does not exist. Reversing those two
     * lines would break this and nothing else would notice.
     *
     * The **rollback** half is not asserted here and cannot be: a unit test has no transaction.
     * § 10 carries it as a named deferral rather than leaving it silently absent.
     */
    @Test
    fun create_doesNotPublish_whenPersistenceFails() {
        whenever(repository.save(any())).thenThrow(IllegalStateException("could not extract ResultSet"))

        assertThatThrownBy { driver.create(command) }
            .isInstanceOf(IllegalStateException::class.java)

        verify(eventPublisher, never()).publish(any())
    }

    /**
     * The § 2.3 pairing rule, at the use-case level.
     *
     * Not an `AC-NN`: § 7's criteria are the eight the spec agrees to, and this rule was found
     * during implementation rather than specified. It is asserted here as well as at
     * `PriceRangeTest` because the driver is where the two nullable command fields collapse
     * into one nullable domain type, so a driver that mapped them wrongly — silently dropping
     * a lone bound, say — would leave `PriceRangeTest` green.
     */
    @Test
    fun create_throwsInvalidMasterLeasingContractException_whenOnlyOnePriceBoundSupplied() {
        assertThatThrownBy { driver.create(command.copy(priceRangeMax = null)) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)

        verify(repository, never()).save(any())
    }
}
