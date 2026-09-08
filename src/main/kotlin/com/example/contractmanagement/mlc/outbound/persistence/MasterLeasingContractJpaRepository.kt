package com.example.contractmanagement.mlc.outbound.persistence

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContract
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractId
import com.example.contractmanagement.mlc.core.outport.MasterLeasingContractRepository
import org.springframework.stereotype.Repository

/**
 * JPA-backed adapter for the [MasterLeasingContractRepository] outbound port.
 *
 * **Both rows are written here rather than by a JPA cascade**, and that is the one design
 * choice in this class. A `@OneToOne(cascade = ALL)` from the contract to its configuration
 * would work, and it would also make the write order, the orphan handling and the flush timing
 * properties of a Hibernate annotation rather than of code a test can read. The aggregate owns
 * its configuration, so the adapter persists the aggregate — configuration first, because the
 * contract row carries `mlc_config_id` and a database that later gains a foreign key there
 * would reject the other order.
 *
 * There is no `update`: the port has none, because no use case changes a contract yet
 * (`uc07` § 6). When one arrives, note the lesson recorded in
 * `documentation/ports/tour-booking-repository.outport.spec.md` § 2.3 — a partial update once
 * discarded an entire use case's effect while every test stayed green, so an update here must
 * write the whole aggregate rather than the fields its caller happens to change.
 *
 * [findById] loads both rows and returns null if either is missing. A contract whose
 * configuration row has vanished is not a contract with absent terms — it is a broken
 * aggregate, and returning a half-built one would hand the core something no invariant
 * describes.
 */
@Repository
internal class MasterLeasingContractJpaRepository(
    private val contractRepository: MasterLeasingContractJpaEntityRepository,
    private val configurationRepository: ContractConfigurationJpaEntityRepository,
) : MasterLeasingContractRepository {
    private val mapper = MasterLeasingContractMapper()

    override fun save(contract: MasterLeasingContract) {
        configurationRepository.save(mapper.toConfigurationEntity(contract.currentConfiguration))
        contractRepository.save(mapper.toContractEntity(contract))
    }

    override fun findById(id: MasterLeasingContractId): MasterLeasingContract? =
        contractRepository.findById(id.value.toString()).orElse(null)?.let { contract ->
            configurationRepository.findById(contract.mlcConfigId).orElse(null)?.let { configuration ->
                mapper.toDomain(contract, configuration)
            }
        }
}
