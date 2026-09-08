# ADR 0018 – Lifecycle Transitions Belong to the Aggregate

## Status
Accepted

## Context

The contract lifecycle *is* the MVP. Its scope lists, for both contract levels, a status
lifecycle from active to ended, and the risk register asks how statuses can be used **as
rules** — which check mechanisms can be expressed over them.

The service this repository takes its stack from uses `spring-statemachine` for exactly
this: a state machine service per case type, with transitions, events and guards declared in
framework configuration.

`architecture.definition.md` requires a framework-free core, and `DependencyRulesTest`
enforces it. A state machine driving an aggregate's status therefore cannot live in the
aggregate's own package; it lives outside, and the aggregate becomes the thing it moves.

The lifecycle is also not settled. The MVP page carries an open question against both
levels: *which statuses do we still need here?*

## Decision

**Transitions are methods on the aggregate.** `MasterLeasingContract.terminate(...)` checks
its own preconditions and either transitions or throws a domain exception. Status is a
sealed set of values owned by the context, and no type outside the aggregate may set it.

`spring-statemachine` is not used, in either context. The dependency is not added.

The transition diagram, when one is needed for humans, lives in the context's domain spec
under `documentation/domain/`.

## Rationale

**The rules that matter here are not transitions.** "A master contract may not be terminated
while leases are active under it" is a question asked across a context boundary. A state
machine expresses it as a guard, and that guard has to call into another context — which
means the most important business rule in the MVP would live in framework configuration,
where neither the domain spec nor a domain unit test can see it.

**An unsettled lifecycle argues against declarative configuration.** Statuses will change:
the open question says so. Changing an enum and a method with a failing test in front of it
is a compile-time exercise. Changing a state machine's configuration is a runtime one, and
its failure mode is a transition that silently no longer fires.

**The donor's own outcome is the evidence.** In that service the domain packages carry
`@Entity` and Spring types, and no rule ever stopped it. Adopting the same lifecycle
mechanism would reproduce the same drift by the same route, in the one place this
repository's gates are strongest.

## Consequences

- There is no single generated picture of all transitions. The domain spec carries it, and
  it can go stale — a known cost, mitigated by the transitions being few and by unit tests
  naming the illegal ones.
- Every transition is unit-testable without Spring. This is the main practical benefit and
  it applies from the first increment.
- If a future contract type genuinely needs orchestration across many states and long-lived
  waits, that is a new decision and a new ADR — not a reinterpretation of this one.
