# ADR 0017 – The Contract Is Ours; Its Participants Are Not

## Status
**Withdrawn** — its subject no longer exists.

It drew an ownership boundary between the contract (ours) and its participants — employer,
lessor, partner number — held in Radar and Odoo behind an anti-corruption layer. The current
scope has no external participant systems and no foreign identities: a Master carries its own
name and customer number, and nothing about it is mastered elsewhere.

Its general argument is still worth reading before the first outbound integration arrives.

## Context

Radar and Odoo are the leading systems for JobRad's contract data today. The MVP's risk
register states it directly: depending on the migration strategy, the project needs
duplicated data and APIs towards Radar and Odoo *as the leading systems at the moment*.
Odoo is described elsewhere as the central system of record.

This collides with the modelling doctrine in `modelling.definition.md`. An Always-Valid
aggregate owns its invariants. An aggregate whose authority lives in another system owns
nothing: it mirrors, and `ddd-hex-reviewer` correctly reports a mirror as an anemic
aggregate.

Taking that at face value would leave two options, both bad — declare the domain ours and
pretend Odoo is not authoritative for employer data, or declare the domain a read model and
give up the invariants this service exists to express.

The data model page resolves it, and had already resolved it before the question was asked.
Every participant field is marked **external** there — `employer_id`, `job_cyclist_id`,
`bike_id`, `lessor_id`, `service_provider_id`, `partner_number`. The contract's own fields —
`status`, `cancellation_reason`, `activation_date`, `credit_limit`,
`return_quota_percentage`, the rates and terms — are not.

## Decision

The ownership boundary follows that marking.

**This service owns the contract**: its existence, status, lifecycle transitions,
cancellation grounds, terms, and the link between a master contract and the leases issued
under it. These are invariants, enforced in the aggregate.

**This service does not own the participants**: employer, employee, bike, lessor, service
provider. They enter through an **anti-corruption layer** — an outbound adapter that
translates the foreign representation into this service's value objects. No Odoo or Radar
type, DTO or column name appears in `core` or `shared.domain`.

A participant is held as an opaque identity value, per
`adr/0005-bounded-context-identity-boundaries.adr.md`. Attributes of a participant are
fetched through a port when a use case needs them, and are not stored as a shadow copy
unless a specification says so and states how it is refreshed.

## Rationale

**It is the boundary the domain already has.** The external markings were written by the
people who know which system decides what. Adopting them costs nothing and encodes
knowledge this repository does not otherwise hold.

**It keeps the invariants where they can be enforced.** "A master contract cannot be
terminated while leases are active under it" needs no permission from Odoo — both sides of
that rule are ours. "The employer's address changed" is not our rule at all, and the MVP
page explicitly assigns it to another domain.

**A shadow copy is a decision, not a default.** Duplicated data is on the project's risk
register. Making every foreign attribute a port call by default means each stored copy is
introduced deliberately, with a spec saying when it goes stale — instead of a table that
accumulated columns because they were convenient at the time.

## Consequences

- A use case needing employer or employee attributes makes a port call. In the MVP those
  ports are stubs, and every stub is named in the use case's spec.
- Denormalising later is allowed and requires a spec section stating the refresh rule.
- `DependencyRulesTest` already forbids framework and persistence types in the core; the
  ACL obligation is broader than what a rule can currently check, so it is written in
  `coding-style.definition.md` § 9 as a rule whose owner is review — the honest admission
  that table exists for.
- Outbound direction is not covered here. Writing our contract facts back to Radar and Odoo
  is `adr/0019-outbound-synchronisation-through-an-outbox.adr.md`.
