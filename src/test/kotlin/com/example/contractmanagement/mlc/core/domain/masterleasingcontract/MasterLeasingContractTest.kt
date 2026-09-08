package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.event.MasterLeasingContractCreated
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Domain tests for the [MasterLeasingContract] aggregate root (UC07).
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md` and
 * `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`.
 *
 * The status assertion rests on **PD-07** and the timestamp assertions on **PD-08** — both
 * provisional decisions of ours. PD-07 is the closest of the eleven to its source (the MVP
 * page's *"Aktiv → Beendet"* reads as "created active"); what keeps it provisional is the same
 * line's unresolved *"which statuses do we still need here?"*.
 */
class MasterLeasingContractTest {
    private val now: Instant = Instant.parse("2026-09-08T09:30:00Z")

    private fun create(configuration: ContractConfiguration = MlcTestData.configuration()) =
        MasterLeasingContract.create(
            id = MasterLeasingContractId.generate(),
            employerId = EmployerId("emp-1"),
            lessorId = LessorId("lessor-1"),
            partnerNumber = PartnerNumber("p-4711"),
            owner = "contract-operations",
            configuration = configuration,
            now = now,
        )

    @Test
    fun create_setsStatusActive() {
        assertThat(create().status).isEqualTo(MasterLeasingContractStatus.ACTIVE)
    }

    @Test
    fun create_pointsAtExactlyOneCurrentConfiguration() {
        val configuration = MlcTestData.configuration()

        val contract = create(configuration)

        assertThat(contract.currentConfiguration).isSameAs(configuration)
        assertThat(contract.currentConfiguration.version).isEqualTo(1)
    }

    @Test
    fun create_usesTheSuppliedInstant_forCreationTimeAndActivationDate() {
        val contract = create()

        assertThat(contract.creationTime).isEqualTo(now)
        assertThat(contract.activationDate).isEqualTo(now)
    }

    @Test
    fun create_recordsMasterLeasingContractCreated() {
        val contract = create()

        assertThat(contract.pullDomainEvents())
            .singleElement()
            .isEqualTo(MasterLeasingContractCreated(contract.id, now))
    }

    @Test
    fun pullDomainEvents_returnsEmptyList_onSecondCall() {
        val contract = create()

        contract.pullDomainEvents()

        assertThat(contract.pullDomainEvents()).isEmpty()
    }

    @Test
    fun reconstitute_recordsNoEvent() {
        val contract =
            MasterLeasingContract.reconstitute(
                id = MasterLeasingContractId.generate(),
                employerId = EmployerId("emp-1"),
                lessorId = LessorId("lessor-1"),
                partnerNumber = null,
                owner = null,
                status = MasterLeasingContractStatus.ACTIVE,
                creationTime = now,
                activationDate = now,
                currentConfiguration = MlcTestData.minimalConfiguration(),
            )

        assertThat(contract.pullDomainEvents()).isEmpty()
    }
}
