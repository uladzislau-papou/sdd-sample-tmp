package com.example.contractmanagement.mlc.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository over [ContractConfigurationJpaEntity].
 *
 * `internal` for the same reason as [MasterLeasingContractJpaEntityRepository]. Note that a
 * configuration is **not** independently reachable from the core: there is no outport for it,
 * because it is an entity inside the `MasterLeasingContract` aggregate and loading one without
 * its contract would hand a caller a fragment of an aggregate.
 */
internal interface ContractConfigurationJpaEntityRepository : JpaRepository<ContractConfigurationJpaEntity, String>
