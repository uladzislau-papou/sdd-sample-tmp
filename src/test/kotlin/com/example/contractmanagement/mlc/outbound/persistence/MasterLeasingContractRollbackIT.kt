package com.example.contractmanagement.mlc.outbound.persistence

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContract
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractId
import com.example.contractmanagement.mlc.core.inport.command.CreateMasterLeasingContractCommand
import com.example.contractmanagement.mlc.core.inport.usecase.CreateMasterLeasingContractUseCase
import com.example.contractmanagement.mlc.core.outport.MasterLeasingContractRepository
import com.example.contractmanagement.support.PostgresIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

/**
 * UC07 § 8, third bullet: **the transaction rolls back and no contract exists.**
 *
 * **This test exists because the claim was specified and then deferred to the wrong owner.**
 * § 8 stated it; nothing asserted it. UC07's first attempt recorded the whole bullet as
 * deferred to the outbox increment on the grounds that "the atomicity claim is `adr/0022`'s".
 * `conformance-reviewer` refuted that: the outbox *row's* atomicity with the aggregate is indeed
 * `adr/0022`'s and needs the unbuilt adapter, but the **aggregate's own rollback** is this
 * driver's `@Transactional` boundary and needs nothing that does not exist. It was UC07's debt
 * being pushed onto work that did not own it.
 *
 * **Why the failure is injected after a flush, which is the whole design of this test.**
 * The obvious version — feed the driver a command that violates a database constraint — proves
 * almost nothing here. Spring Data's `save` on an entity with an assigned identifier calls
 * `persist`, which issues no SQL; both inserts would share one commit-time flush, both would
 * fail together, and the assertion "no rows afterwards" would pass just as happily if nothing
 * had ever been written. A test that cannot distinguish *rolled back* from *never attempted* is
 * not a rollback test.
 *
 * So [FailingRepositoryConfig] wraps the **real** adapter: it saves, calls
 * `EntityManager.flush()` so both rows genuinely reach the database inside the driver's
 * transaction, and only then throws. The rows existed; the assertion is that they do not
 * survive. `JdbcTemplate` reads them back outside the driver's transaction, and the test class
 * is deliberately **not** `@Transactional` — a test-managed transaction would roll everything
 * back at the end and make the assertion vacuous.
 *
 * SDD: `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 8, and
 * `documentation/ports/master-leasing-contract-repository.outport.spec.md` § 2.1 ("atomic").
 */
@SpringBootTest
@Import(MasterLeasingContractRollbackIT.FailingRepositoryConfig::class)
class MasterLeasingContractRollbackIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var useCase: CreateMasterLeasingContractUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val command =
        CreateMasterLeasingContractCommand(
            employerId = "emp-rollback",
            lessorId = "lessor-1",
            partnerNumber = null,
            owner = null,
            creditLimit = BigDecimal("10000"),
            contractType = "salary-sacrifice-leasing",
            currency = "EUR",
            eligibleEmployees = 5,
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

    @Test
    @DisplayName("§ 8: a failure after the aggregate was written leaves no contract and no terms")
    fun create_persistsNothing_whenTheContractRowViolatesAConstraint() {
        val contractsBefore = countContracts()
        val configurationsBefore = countConfigurations()

        assertThatThrownBy { useCase.create(command) }
            .isInstanceOf(SimulatedPersistenceFailure::class.java)

        assertThat(countContracts())
            .describedAs("the contract row must not survive the rolled-back transaction")
            .isEqualTo(contractsBefore)
        assertThat(countConfigurations())
            .describedAs(
                "the configuration row must not survive either — the terms are part of the " +
                    "aggregate, so a half-committed contract is worse than none",
            ).isEqualTo(configurationsBefore)
        assertThat(countContractsFor("emp-rollback")).isZero()
    }

    private fun countContracts(): Int = count("SELECT count(*) FROM master_leasing_contract")

    private fun countConfigurations(): Int = count("SELECT count(*) FROM mlc_configuration")

    private fun countContractsFor(employerId: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM master_leasing_contract WHERE employer_id = ?",
            Int::class.java,
            employerId,
        ) ?: 0

    private fun count(sql: String): Int = jdbcTemplate.queryForObject(sql, Int::class.java) ?: 0

    /** Thrown by the wrapper so the assertion cannot be satisfied by an unrelated failure. */
    class SimulatedPersistenceFailure : RuntimeException("simulated failure after the aggregate was written")

    /**
     * Replaces the repository bean with one that writes through the real adapter, flushes, and
     * then fails.
     *
     * `@Primary` rather than a mock, deliberately: a mock would never issue the SQL, and this
     * test's entire subject is whether SQL that *was* issued gets undone.
     */
    @TestConfiguration
    class FailingRepositoryConfig {
        @Bean
        @Primary
        internal fun failingRepository(
            delegate: MasterLeasingContractJpaRepository,
            entityManager: EntityManager,
        ): MasterLeasingContractRepository =
            object : MasterLeasingContractRepository {
                override fun save(contract: MasterLeasingContract) {
                    delegate.save(contract)
                    entityManager.flush()
                    throw SimulatedPersistenceFailure()
                }

                override fun findById(id: MasterLeasingContractId): MasterLeasingContract? = delegate.findById(id)
            }
    }
}
