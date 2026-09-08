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
import com.example.contractmanagement.mlc.core.outport.MasterLeasingContractRepository
import com.example.contractmanagement.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.Instant

/**
 * Adapter integration test for [MasterLeasingContractJpaRepository] against a real PostgreSQL.
 *
 * `replace = NONE` because `@DataJpaTest` would otherwise swap in an embedded database and
 * quietly undo the point of the test. Flyway is imported explicitly: `@DataJpaTest` does not
 * auto-configure it, and with `ddl-auto: validate` the schema has to come from the migrations
 * — which means this test also proves `V3__DDL_create_master_leasing_contract.sql` and the
 * entity mappings agree.
 *
 * That agreement carries more weight here than usual. This aggregate has **twenty-one**
 * persisted columns across two tables plus an element collection, and the failure mode of a
 * wide mapping is a single column silently dropped — which no domain or driver test can see,
 * because both observe the aggregate in memory. Hence
 * [save_thenFindById_roundTripsEveryTerm] asserting every field rather than a representative
 * few: `uc07` § 3 records that the outport spec's own § 2.3 lesson was a partial write that
 * kept every test green because each test asserted only the field it cared about.
 *
 * SDD: adapter integration test per `documentation/test.definition.md` § 2.3, and
 * `documentation/ports/master-leasing-contract-repository.outport.spec.md`.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
@Import(MasterLeasingContractJpaRepository::class)
class MasterLeasingContractJpaRepositoryIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var repository: MasterLeasingContractRepository

    private val creationTime: Instant = Instant.parse("2026-09-08T09:30:00Z")

    private fun contractWithEveryTerm(): MasterLeasingContract =
        MasterLeasingContract.create(
            id = MasterLeasingContractId.generate(),
            employerId = EmployerId("emp-1"),
            lessorId = LessorId("lessor-1"),
            partnerNumber = PartnerNumber("p-4711"),
            owner = "contract-operations",
            configuration =
                ContractConfiguration.create(
                    id = ContractConfigurationId.generate(),
                    creditLimit = CreditLimit(BigDecimal("50000.00")),
                    contractType = ContractType("salary-sacrifice-leasing"),
                    currency = CurrencyCode("EUR"),
                    eligibleEmployees = EligibleEmployees(250),
                    salesChannel = "direct",
                    groupJointLiability = true,
                    returnQuotaPercentage = Percentage(BigDecimal("10.00")),
                    earlyClaimFeePercentage = Percentage(BigDecimal("2.50")),
                    earlyClaimWindowMonths = 12,
                    noticePeriodRule = "3-months-to-quarter-end",
                    paymentTerms = "NET30",
                    priceRange = PriceRange(BigDecimal("749.00"), BigDecimal("11000.00")),
                    calculationBasis = "sale-price-plus-shipping",
                    servicePackageOptions = listOf("basic", "comfort"),
                    servicePackageVersion = 3,
                    categoriesEditableInPortal = true,
                ),
            now = creationTime,
        )

    private fun contractWithOnlyMandatoryTerms(): MasterLeasingContract =
        MasterLeasingContract.create(
            id = MasterLeasingContractId.generate(),
            employerId = EmployerId("emp-2"),
            lessorId = LessorId("lessor-2"),
            partnerNumber = null,
            owner = null,
            configuration =
                ContractConfiguration.create(
                    id = ContractConfigurationId.generate(),
                    creditLimit = CreditLimit(BigDecimal("10000.00")),
                    contractType = ContractType("salary-sacrifice-leasing"),
                    currency = CurrencyCode("CHF"),
                    eligibleEmployees = EligibleEmployees(5),
                    salesChannel = null,
                    groupJointLiability = false,
                    returnQuotaPercentage = null,
                    earlyClaimFeePercentage = null,
                    earlyClaimWindowMonths = null,
                    noticePeriodRule = null,
                    paymentTerms = null,
                    priceRange = null,
                    calculationBasis = null,
                    servicePackageOptions = emptyList(),
                    servicePackageVersion = null,
                    categoriesEditableInPortal = false,
                ),
            now = creationTime,
        )

    @Test
    fun save_thenFindById_roundTripsEveryTerm() {
        val saved = contractWithEveryTerm()

        repository.save(saved)
        val loaded = repository.findById(saved.id)

        requireNotNull(loaded) { "the contract just saved was not found" }
        assertThat(loaded.id).isEqualTo(saved.id)
        assertThat(loaded.employerId).isEqualTo(EmployerId("emp-1"))
        assertThat(loaded.lessorId).isEqualTo(LessorId("lessor-1"))
        assertThat(loaded.partnerNumber).isEqualTo(PartnerNumber("p-4711"))
        assertThat(loaded.owner).isEqualTo("contract-operations")
        assertThat(loaded.status).isEqualTo(MasterLeasingContractStatus.ACTIVE)
        assertThat(loaded.creationTime).isEqualTo(creationTime)
        assertThat(loaded.activationDate).isEqualTo(creationTime)

        val terms = loaded.currentConfiguration
        assertThat(terms.id).isEqualTo(saved.currentConfiguration.id)
        assertThat(terms.version).isEqualTo(1)
        assertThat(terms.creditLimit.value).isEqualByComparingTo(BigDecimal("50000.00"))
        assertThat(terms.contractType).isEqualTo(ContractType("salary-sacrifice-leasing"))
        assertThat(terms.currency).isEqualTo(CurrencyCode("EUR"))
        assertThat(terms.eligibleEmployees).isEqualTo(EligibleEmployees(250))
        assertThat(terms.salesChannel).isEqualTo("direct")
        assertThat(terms.groupJointLiability).isTrue()
        assertThat(terms.returnQuotaPercentage?.value).isEqualByComparingTo(BigDecimal("10.00"))
        assertThat(terms.earlyClaimFeePercentage?.value).isEqualByComparingTo(BigDecimal("2.50"))
        assertThat(terms.earlyClaimWindowMonths).isEqualTo(12)
        assertThat(terms.noticePeriodRule).isEqualTo("3-months-to-quarter-end")
        assertThat(terms.paymentTerms).isEqualTo("NET30")
        assertThat(terms.priceRange?.min).isEqualByComparingTo(BigDecimal("749.00"))
        assertThat(terms.priceRange?.max).isEqualByComparingTo(BigDecimal("11000.00"))
        assertThat(terms.calculationBasis).isEqualTo("sale-price-plus-shipping")
        assertThat(terms.servicePackageOptions).containsExactly("basic", "comfort")
        assertThat(terms.servicePackageVersion).isEqualTo(3)
        assertThat(terms.categoriesEditableInPortal).isTrue()
    }

    /**
     * The absent-optional path, at the database.
     *
     * Every optional column is nullable because **PD-01** says so, and PD-01 is provisional —
     * so this test is also the evidence that a `NOT NULL` added later would break existing
     * rows, which is the asymmetry PD-01 cites for erring toward fewer mandatory fields.
     */
    @Test
    fun save_persistsOptionalTermsAsNull_whenAbsent() {
        val saved = contractWithOnlyMandatoryTerms()

        repository.save(saved)
        val loaded = repository.findById(saved.id)

        requireNotNull(loaded) { "the contract just saved was not found" }
        assertThat(loaded.partnerNumber).isNull()
        assertThat(loaded.owner).isNull()

        val terms = loaded.currentConfiguration
        assertThat(terms.salesChannel).isNull()
        assertThat(terms.returnQuotaPercentage).isNull()
        assertThat(terms.earlyClaimFeePercentage).isNull()
        assertThat(terms.earlyClaimWindowMonths).isNull()
        assertThat(terms.noticePeriodRule).isNull()
        assertThat(terms.paymentTerms).isNull()
        assertThat(terms.priceRange).isNull()
        assertThat(terms.calculationBasis).isNull()
        assertThat(terms.servicePackageOptions).isEmpty()
        assertThat(terms.servicePackageVersion).isNull()
        assertThat(terms.groupJointLiability).isFalse()
        assertThat(terms.categoriesEditableInPortal).isFalse()
    }

    @Test
    fun findById_returnsNull_whenNoContractHasThatIdentity() {
        assertThat(repository.findById(MasterLeasingContractId.generate())).isNull()
    }
}
