package com.example.contractmanagement.mlc.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence row type for the `master_leasing_contract` table.
 *
 * Lives in `outbound.persistence` and **never** in `core`. That is the one persistence rule
 * this repository fixes, and it is enforced rather than agreed: `DependencyRulesTest` fails if
 * `jakarta.persistence` is reachable from `core` (`adr/0011`).
 *
 * The domain's aggregate and this class are mapped by [MasterLeasingContractMapper] and share
 * no types. The duplication is deliberate — it is what lets a column be renamed without
 * touching a domain invariant, and an invariant to change without a migration. That matters
 * more here than usual: **PD-10** is expected to change, and `kuv_joint_liability` on the
 * configuration row keeps the source's German abbreviation while the domain calls it
 * `groupJointLiability` (**PD-00**). Neither could happen if the two sides shared a type.
 *
 * [mlcConfigId] is a plain column and not a JPA relationship. The configuration is an entity
 * *inside* this aggregate, so the adapter writes both rows itself rather than delegating to a
 * cascade — see [MasterLeasingContractJpaRepository] for why.
 *
 * `LongParameterList` is suppressed for the reason it is on the aggregate: a row type
 * assembles a whole row in one constructor, so the count is a property of the table.
 *
 * SDD: see `documentation/ports/master-leasing-contract-repository.outport.spec.md`.
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "master_leasing_contract")
class MasterLeasingContractJpaEntity(
    @Id
    @Column(name = "id", nullable = false, length = 36)
    var id: String = "",
    @Column(name = "employer_id", nullable = false)
    var employerId: String = "",
    @Column(name = "lessor_id", nullable = false)
    var lessorId: String = "",
    @Column(name = "partner_number")
    var partnerNumber: String? = null,
    @Column(name = "owner")
    var owner: String? = null,
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "",
    @Column(name = "creation_time", nullable = false)
    var creationTime: Instant = Instant.EPOCH,
    @Column(name = "activation_date", nullable = false)
    var activationDate: Instant = Instant.EPOCH,
    @Column(name = "mlc_config_id", nullable = false, length = 36)
    var mlcConfigId: String = "",
)
