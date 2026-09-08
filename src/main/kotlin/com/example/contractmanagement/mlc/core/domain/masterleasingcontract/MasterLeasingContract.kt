package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.event.MasterLeasingContractCreated
import com.example.contractmanagement.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * Aggregate root representing the framework agreement with an employer — a
 * *Leasingrahmenvertrag* (LRV).
 *
 * It is the record of authority for its own status and terms (`adr/0017`). Nothing downstream
 * in the MVP exists without one: an individual lease is issued *under* an LRV.
 *
 * State changes happen through named transition methods, each enforcing the invariants of that
 * transition and recording the resulting domain event, per
 * `adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`. Today [create] is the only
 * one; `terminate` is blocked on the status list (**PD-07**).
 *
 * Deliberately **not** a `data class`: structural equality is wrong for an entity, and a
 * generated `copy()` would hand every caller a way around the transition methods
 * (`coding-style.definition.md` § 2.1). Mutable state uses `private set` for the same reason
 * (§ 5.1).
 *
 * Framework-free: no Spring, no JPA, no IO.
 *
 * **On the timestamps.** [creationTime] and [activationDate] are both the moment of creation
 * (**PD-08**), supplied by the driver from `ClockPort` and never read here — the domain must
 * not call `Instant.now()` (`architecture.definition.md` § 8) and a GraphQL client must not
 * supply one (§ 8.1, **PD-11**). They are separate fields because the source defines them as
 * separate facts, and PD-07 is what currently makes them equal: a contract that is active on
 * creation activates when it is created. A pre-active status would separate them, which is the
 * change PD-07 names.
 *
 * `LongParameterList` is suppressed for the whole class — see [ContractConfiguration] for the
 * argument, which is the same one.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md` and
 * `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`.
 */
@Suppress("LongParameterList")
class MasterLeasingContract private constructor(
    val id: MasterLeasingContractId,
    val employerId: EmployerId,
    val lessorId: LessorId,
    val partnerNumber: PartnerNumber?,
    val owner: String?,
    val creationTime: Instant,
    val activationDate: Instant,
    status: MasterLeasingContractStatus,
    currentConfiguration: ContractConfiguration,
) {
    var status: MasterLeasingContractStatus = status
        private set

    /**
     * The terms version currently in force — the source's `mlc_config_id`.
     *
     * `private set` because superseding the terms is a transition, and the use case that
     * performs it does not exist yet. When it does, it is a method here and not an assignment
     * from a driver.
     */
    var currentConfiguration: ContractConfiguration = currentConfiguration
        private set

    private val domainEvents = mutableListOf<DomainEvent>()

    /**
     * Returns and clears the recorded domain events.
     *
     * A second call returns an empty list. The snapshot is a copy, so a caller iterating it
     * cannot be surprised by a later transition.
     */
    fun pullDomainEvents(): List<DomainEvent> {
        val snapshot = domainEvents.toList()
        domainEvents.clear()
        return snapshot
    }

    companion object {
        /**
         * Creates a master leasing contract together with the first version of its terms
         * (UC07).
         *
         * The contract is `ACTIVE` from the start (**PD-07**) and its configuration is version
         * 1 — the value [ContractConfiguration.create] defaults to.
         *
         * Every § 2.3 rule has already run by the time this is called: each lives in the value
         * object or in [ContractConfiguration.create] that owns it, which is what keeps this
         * factory free of validation of its own. That is deliberate rather than incidental —
         * a rule duplicated here and in a value object would be a rule with two places to
         * drift, and **PD-10** is expected to change.
         *
         * @param now the moment of creation, supplied by the driver from `ClockPort`
         */
        fun create(
            id: MasterLeasingContractId,
            employerId: EmployerId,
            lessorId: LessorId,
            partnerNumber: PartnerNumber?,
            owner: String?,
            configuration: ContractConfiguration,
            now: Instant,
        ): MasterLeasingContract =
            MasterLeasingContract(
                id = id,
                employerId = employerId,
                lessorId = lessorId,
                partnerNumber = partnerNumber,
                owner = owner,
                creationTime = now,
                activationDate = now,
                status = MasterLeasingContractStatus.ACTIVE,
                currentConfiguration = configuration,
            ).also {
                it.domainEvents += MasterLeasingContractCreated(id, now)
            }

        /**
         * Rebuilds a contract from its persisted state, with no pending events.
         *
         * Called by the persistence mapper, and re-checks no invariants for the reason
         * [ContractConfiguration.reconstitute] gives. `ClassRoleRulesTest` enforces that a
         * caller outside `..outbound.persistence..` is skipping the invariants [create]
         * enforces.
         */
        fun reconstitute(
            id: MasterLeasingContractId,
            employerId: EmployerId,
            lessorId: LessorId,
            partnerNumber: PartnerNumber?,
            owner: String?,
            status: MasterLeasingContractStatus,
            creationTime: Instant,
            activationDate: Instant,
            currentConfiguration: ContractConfiguration,
        ): MasterLeasingContract =
            MasterLeasingContract(
                id = id,
                employerId = employerId,
                lessorId = lessorId,
                partnerNumber = partnerNumber,
                owner = owner,
                creationTime = creationTime,
                activationDate = activationDate,
                status = status,
                currentConfiguration = currentConfiguration,
            )
    }
}
