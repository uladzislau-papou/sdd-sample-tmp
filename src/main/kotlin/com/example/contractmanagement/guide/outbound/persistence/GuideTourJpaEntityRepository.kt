package com.example.contractmanagement.guide.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data repository over [GuideTourJpaEntity]. Internal to this adapter. */
internal interface GuideTourJpaEntityRepository : JpaRepository<GuideTourJpaEntity, String>
