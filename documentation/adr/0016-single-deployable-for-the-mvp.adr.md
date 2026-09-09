# ADR 0016 – One Deployable for the MVP, Not Two Services

## Status
**Withdrawn** — its subject no longer exists.

The question was whether to build one deployable or two, against a platform component view
drawing `MLA Management` and `ILA Management` as separate services. Neither service, neither
context, nor the component view is part of this service's scope any more. Nothing replaced the
decision, so "superseded" would be a lie.

## Context

The platform's component view for Contract Management draws **two services**: `MLA
Management` and `ILA Management`, grouped under a logical unit called Contract Management,
alongside separate onboarding, DMS and partner services.

This repository is a single Spring Boot application with one composition root.
`adr/0015-two-contexts-by-contract-level.adr.md` splits the domain into two bounded
contexts, which is a package boundary, not a deployment boundary.

The MVP's own risk register lists the LRV → ELV dependency logic first, noting that the
linkage rules — limits, entitlements, status dependencies — are not expressed anywhere
today.

## Decision

**One deployable for the MVP.** Both contexts ship in `contract-management-service`.

The boundary between them is enforced at the package level by ArchUnit, so the split into
two deployables remains available. This decision is revisited when **either** of the
following becomes true:

1. The LRV → ELV rules are written down, specified and covered by tests — i.e. the relation
   is understood well enough that a network boundary across it can be reasoned about; or
2. The two levels acquire genuinely different operational needs — separate scaling,
   separate release cadence, separate ownership — rather than a diagram showing two boxes.

## Rationale

**A distributed boundary across an unspecified relation buys nothing and costs a class of
bug.** Inside one process, "a master contract cannot be terminated while leases are active
under it" is a port call inside one transaction: if it is wrong, a test fails. Across a
network boundary the same rule needs retries, idempotency and a decision about what happens
when one side committed and the other did not — and it can be wrong in production without
being wrong in any test. Paying that cost for a rule nobody has written down yet means
learning the rule from incidents.

**The reversible direction is the cheap one.** Splitting a package boundary that ArchUnit
already enforces into two deployables is mechanical. Merging two deployables back after
their APIs have consumers is not.

**Why this is an ADR and not silence.** The component view is somebody's considered
architecture. Quietly shipping one service instead of two would surface months later as a
disagreement nobody could date. This records that the divergence is deliberate, temporary,
and has a stated condition for ending — and it is the artifact to take into that
conversation.

## Consequences

- The component view and this repository disagree until one of them changes. That
  disagreement is written down here rather than discovered.
- Both contexts share a database. Their schemas must stay separable — no foreign key from
  `ilc` tables into `mlc` tables — or the deployment split later becomes a migration
  project. This is the constraint most likely to be violated by accident.
- Cross-context calls stay synchronous and in-process for now, which
  `adr/0008-synchronous-cross-context-cancellation.adr.md` — withdrawn, but kept for its
  reasoning — is the worked example of.
