package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

/**
 * Lifecycle states of a [MasterLeasingContract].
 *
 * **PD-07**, and the set is expected to grow. The MVP page states the lifecycle as
 * *"Aktiv → Beendet"* — which is where these two come from — and the same line carries the
 * unresolved *"ToDo: welche Stati brauchen wir hier noch?"*.
 *
 * An enum and not a state-machine configuration, per
 * `adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`: an unsettled lifecycle
 * argues *against* declarative configuration, because changing an enum and a method with a
 * failing test in front of it is a compile-time exercise.
 *
 * `TERMINATED` is unreachable today — no use case terminates a contract yet, and the one that
 * will is blocked on the status list above. It is declared because the source states the
 * lifecycle has two ends, and because a single-valued status enum invites the next author to
 * ask why the field exists at all.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
enum class MasterLeasingContractStatus {
    ACTIVE,
    TERMINATED,
}
