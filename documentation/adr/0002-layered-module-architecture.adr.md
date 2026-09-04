# ADR 0002 – Layered Module Architecture

## Status

Accepted (retroactive)

Records the structure the codebase has. See ADR 0010 for the target it does not have.

## Context

Risk Management Service covers four distinct capabilities — identification and signature
(`qes`), KYC case management (`kyc`), onboarding integration (`onb`), and credit decision
intake (`boni`) — in one deployable.

Two questions had to be answered: how the capabilities are separated from each other, and
how each is structured internally.

## Decision

### Modules

One top-level package per capability under
`com.jobradleasing.riskmanagementservice`, plus `common` (shared kernel), `web`
(cross-module delivery) and `config`.

Module boundaries are **compile-time conventions, not network boundaries**. The service is
a modular monolith: one JAR, one database, one deployment.

Cross-module dependencies are **permitted and registered** rather than forbidden. The
actual graph is recorded in `architecture.definition.md` § 11.2. Forbidding what the code
already does would make the architecture document false on the day it was written.

### Internal layering

Inside a module:

```
api/        controller · dto · input · mapper · integration
service/    business logic, transactions, schedulers
repository/ Spring Data JPA
domain/     model (@Entity) · enums · exception
```

with `config/`, `validation/`, `web/`, `audit/`, `auth/`, `util/` as needed.

Direction of dependency: `api → service → repository → domain`. `domain` depends on
nothing in the module.

### The rules that are enforced

Not "the layers are clean" — that would be aspirational. The enforced set is narrow and
currently true (`architecture.definition.md` § 6):

1. No controller injects a repository.
2. No entity crosses an API boundary.
3. `domain` does not import `api` or `service` (four registered exceptions).
4. Transactions open in `service` (four registered exceptions).
5. Provider models do not escape `api/integration`.
6. `common` depends on no feature module.
7. Cross-module edges are registered.
8. Every reachable endpoint is scope-protected or documented as open.

## Consequences

**Positive**

- A reader who knows the four-layer shape can navigate any module immediately.
- The enforced rules are all mechanically checkable, so `rms-architecture-reviewer` can
  return a verdict rather than an opinion.
- One deployment means a schema change and the code that needs it ship together.

**Negative / accepted**

- **Business logic lives in services, not in the model.** Entities are largely state
  carriers. This is the shape the JPA decision (ADR 0003) produces, and it is the classic
  "anemic model" that DDD literature warns against. Accepted: the alternative is a second
  model plus a mapping layer, which is a large cost for a benefit this codebase has not
  been shown to need.
- **Modules are coupled.** `onb → kyc` is 117 imports deep, and the base exception
  taxonomy lives in `qes` and is imported everywhere. Registering the graph makes the
  coupling visible; it does not make it small.
- **A module cannot be extracted cheaply.** Extracting `kyc` would require severing
  `onb → kyc` first. That is a known and accepted property of the current design.
- Layer discipline depends on review, not on a compiler or a module system. There is no
  ArchUnit suite and no JPMS.

**Follow-ups this ADR does not resolve**

- Moving the exception taxonomy from `qes` to `common`
  (`architecture.definition.md` § 11.3).
- Whether `onb → kyc` should go through a narrower published interface.
- Whether an ArchUnit or Konsist suite should mechanise the eight rules above. That would
  be trigger 2 (new dependency).

## Alternatives considered

- **Ports & Adapters per module.** Rejected as the current rule; retained as a target in
  ADR 0010. The migration cost across ~580 production files is not repaid by any problem
  the service currently has.
- **Separate services per capability.** Rejected. The capabilities share a database, a
  release cadence and an operating team; splitting them buys distributed-systems problems
  and no autonomy.
- **Strict no-cross-module-imports with an event bus between modules.** Rejected. `onb`
  exists specifically to read and project `kyc` state; routing that through events would
  make a synchronous read into an eventually-consistent one for no gain.
