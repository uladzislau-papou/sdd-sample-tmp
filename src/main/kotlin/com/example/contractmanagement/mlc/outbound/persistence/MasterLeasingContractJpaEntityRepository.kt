package com.example.contractmanagement.mlc.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository over [MasterLeasingContractJpaEntity].
 *
 * `internal` on purpose: it is an implementation detail of
 * [MasterLeasingContractJpaRepository], and the rest of the service must reach persistence
 * only through the outbound port. A `JpaRepository` leaking into a driver would put paging,
 * `Example` queries and entity types into the core.
 */
internal interface MasterLeasingContractJpaEntityRepository : JpaRepository<MasterLeasingContractJpaEntity, String>
