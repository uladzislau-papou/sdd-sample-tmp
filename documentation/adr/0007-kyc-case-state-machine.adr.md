# ADR 0007 – Spring Statemachine for the KYC Case Lifecycle

## Status

Accepted (retroactive)

## Context

A KYC case moves through a long lifecycle driven by several independent actors:

- **the system** — company intake, core-data ordering, screening submission, research
  polling, transparency-register reconciliation,
- **an operator** — accept, decline, request documents, confirm party collection,
- **external providers** — KYCnow results arriving asynchronously,
- **schedulers** — polling and delivery jobs advancing state on their own clock.

Statuses include `OPEN`, `CORE_DATA_ORDERED`, `PARTIES_COLLECTED`, `SCREENING_CLEARED`,
`SCREENING_REVIEW`, `ACCEPTED`, `DECLINED` and others.

The failure mode this decision had to prevent: transition legality scattered across a
dozen services, each with its own status check, diverging over time — so that a case can
reach a state no one intended by a path no one reviewed.

## Decision

**Transition legality for the KYC case is declared in one place: a Spring Statemachine
configuration**, `kyc/statemachine/` with `KycCaseStateMachineService` as the entry point
and `KycCaseStatusMachineConfig` as the declaration.

- `KycCaseStatus` is the state set; `KycCaseEvent` is the event set.
- A service that wants to advance a case raises an **event** through the machine; it does
  not assign `status` directly.
- An illegal transition produces `BadUserInputException` naming the case, the attempted
  event and the current status.
- `KycCase` carries `@Version`, so two concurrent transitions conflict at commit rather
  than silently last-writer-wins.

### Where a service may still check status directly

Only to produce a **different error message the API contract requires**, and the
duplication is documented at the site. `KycCaseDecisionService.collectParties` is the
precedent: it rejects a non-`CORE_DATA_ORDERED` case with a specific message before
raising the event.

A status check that exists merely to pre-empt the machine is duplication and is drift.

## Consequences

**Positive**

- The legal transition graph is readable in one file, which is what makes it reviewable
  against a compliance requirement.
- Adding a status or event is a visible, single-place change.
- Every caller — operator mutation, provider callback, scheduler — is subject to the same
  rules, because they all go through the same machine.
- Optimistic locking turns a concurrency race into a rejected transaction rather than an
  illegal state.

**Negative / accepted**

- **A dependency on `spring-statemachine-core` 4.0.1** for one module's lifecycle.
  Justified by that lifecycle's size; it would be over-engineering for a three-state
  entity, and `qes` session status deliberately does not use it.
- **Indirection.** "Where does `ACCEPTED` get set?" is answered by reading a configuration
  class rather than by following a call. Discoverability is worse; centralisation is
  better. This is the trade.
- **Two mechanisms for lifecycle in one codebase.** `kyc` uses the machine; `qes` session
  status is advanced in services. A reader must know which module they are in. Accepted:
  unifying would mean either adopting the machine where it is not needed or abandoning it
  where it is.
- Guard conditions inside the machine configuration are business logic living outside a
  `@Service`, which sits awkwardly with `architecture.definition.md` § 4.4. Accepted for
  guards that only inspect the case's own state; anything needing another row belongs in
  the service that raises the event.

## Constraints

- Adding a status or an event: state machine configuration first, enum second, Flyway
  migration third if persisted (`modelling.definition.md` § 2.6).
- Every transition a regulator cares about emits a catalogued audit event
  (ADR 0008).
- Every legal transition and every illegal transition the contract names has a test
  (`test.definition.md` § 2.1).

## Alternatives considered

- **Status checks in each service.** Rejected — precisely the divergence this decision
  exists to prevent.
- **A hand-rolled transition table** (`Map<Status, Set<Event>>`). A reasonable option, and
  materially lighter than the dependency. Rejected because the machine also gives guards,
  actions and listeners in one model, which the table would grow into.
- **Database-enforced transitions** (a check constraint or trigger). Rejected — illegible,
  untestable at the unit level, and produces a constraint-violation error rather than a
  meaningful business message.
