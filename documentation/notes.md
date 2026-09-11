---
type: note
goal: Humans take notes and share them
---

# Notes

Non-authoritative scratchpad. May not contradict formal definitions
(`file-usage.definition.md`).

## Inheritance mode is specified but not implemented

`MLC_CONFIGURATION.inheritance_mode` and `ILC_CONFIGURATION.inheritance_mode`
distinguish *live-linked* from *copied-once* inheritance of terms. Only
`COPIED_ONCE` is implemented: UC05 reads the master contract's terms through
`ReadLeasingTermsUseCase` and copies the ones it needs onto the lease's own
configuration.

`LIVE_LINKED` is the interesting one and is deliberately deferred, because it is
not a feature so much as a question about the context boundary. A live link means
the lease's effective terms change when the master contract is amended, which
means either:

- `individualleasing` reads `masterleasing`'s configuration on every access —
  making the master contract's inport a query hot path and the lease's own terms
  partially undefined without it; or
- amending a master configuration (UC03) fans out to every live-linked lease under
  it, which is a third cross-context interaction with its own consistency question
  — and by `architecture.definition.md` § 10 the choice between event and shared
  transaction would need making explicitly.

The second reading is probably right and it is an ADR, not an increment.

## The data model has a `dynamic_fields` slot

`MLC_CONFIGURATION.dynamic_fields` is described in the source data model as "a
named extensibility slot for future fields; no concrete fields were ever defined".
It is not modelled, and nothing here should model it until somebody names a field
that needs it. An untyped bag on an aggregate is the opposite of everything
`modelling.definition.md` asks for, and "we might need it" has never in the history
of software been a sufficient reason for one.

Worth revisiting only with a concrete field and an ADR.

## Missing read side

`architecture.definition.md` § 4.6 documents an `outbound.persistence.read` side
that currently has no implementation: every use case is a command. A GraphQL API
makes this gap more visible than a REST one did, because the natural shape of a
`query` is a projection and the natural shape of the CQRS half we have is not.

A "list contracts for an employer" query would exercise it and is the obvious
first read-side use case.

## Affiliated contracts are in the model and not in the code

`parent_mlc_id` and `parent_service_agreement_id` carry the corporate-group
structure: a base contract with affiliated contracts hanging off it, and
`kuv_joint_liability` recording whether the group shares liability. The aggregate
carries `parentMasterLeasingContractId` as a nullable reference so the shape is
not lost, but no use case creates or reasons about the hierarchy, and no invariant
guards it — nothing today stops a cycle.

That is a known gap rather than a design: a cycle check is a genuine invariant and
it spans aggregates, which by `modelling.definition.md` means it is either a domain
service reading a chain, or the boundary is wrong. Not decided.
