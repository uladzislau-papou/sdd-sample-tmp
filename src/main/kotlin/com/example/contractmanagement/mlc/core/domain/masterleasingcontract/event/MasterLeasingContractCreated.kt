package com.example.contractmanagement.mlc.core.domain.masterleasingcontract.event

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractId
import com.example.contractmanagement.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * A master leasing contract came into existence (UC07).
 *
 * **Coined, not sourced.** No source names any event or says what one carries.
 * `adr/0019` requires a spec to name the events it emits and `modelling.definition.md` fixes
 * past tense, so this name is invented in `uc07` § 5 step 7 rather than adopted — and because
 * it becomes the outbox record's type, it is a consumer-visible contract: renaming it later
 * breaks whoever reads the outbox.
 *
 * The payload is derived and deliberately minimal: the identifier, because the dispatcher
 * needs to find the contract, and the moment, because a fact's own timestamp is part of the
 * fact. Nothing in the MVP requires more, and every field added here is a field a consumer
 * may come to depend on.
 *
 * [occurredAt] is the moment supplied by the driver from `ClockPort` — never read here.
 * `architecture.definition.md` § 8 forbids the domain from calling `Instant.now()`, and § 8.1
 * forbids a GraphQL client from supplying it (**PD-11**).
 */
data class MasterLeasingContractCreated(
    val masterLeasingContractId: MasterLeasingContractId,
    val occurredAt: Instant,
) : DomainEvent
