# ADR 0021 – Operational Baseline Taken From the Platform's Other Service

## Status
Accepted

## Context

`sdd.playbook.md` § 6 makes a new dependency (trigger 2), new infrastructure (trigger 3)
and a new cross-cutting concern (trigger 11) all ADR-requiring. The operational tooling
below is all three, so it cannot arrive as a quiet commit to `build.gradle.kts`.

`risk-management-service` runs on this platform already and has answers for metrics, git
hooks and coverage reporting. Those answers are worth adopting not because they are the best
available in the abstract, but because an operator debugging two services at 3am should not
have to learn two different observability stacks.

## Decision

Adopt, from that service:

- **Actuator plus micrometer**, exporting **OTLP** and **Prometheus**. The platform already
  collects both.
- **lefthook** git hooks: `spotlessApply` pre-commit, `detektMain detektTest` pre-commit,
  `test` pre-push.
- **jacoco**, producing a coverage report.

Explicitly **not** adopted:

- **`spring-statemachine`** — `adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`
- **minio / the AWS S3 SDK** — contract documents live in the external DMS; a store of our
  own would be built to be thrown away
- **Webhook delivery and the audit outbox as a feature** — no consumer in the MVP. The
  outbox mechanism itself is `adr/0019-outbound-synchronisation-through-an-outbox.adr.md`
- **springdoc-openapi** — `adr/0020-graphql-as-the-only-transport.adr.md`
- **Playwright smoke tests** — the UI is not in this repository

Two decisions inside this one are worth stating on their own.

**jacoco reports; it does not block.** The canonical merge-gate list is
`test.definition.md` § 7 and a coverage threshold is deliberately absent from it.

**The pre-commit hook runs `detektMain detektTest`, not `detekt`.** The convenience task
performs no type resolution and finds strictly less. A hook running it would go green while
`check` goes red, which is worse than no hook: it teaches that the hook means something.

## Rationale

**Why a coverage number is not a gate.** A threshold that blocks a merge gets defended, and
the cheapest defence is testing what is easy rather than what is risky. The riskiest area
here is the integration with Radar and Odoo — behaviour against systems whose behaviour is
undocumented — and coverage cannot measure it. The report is still worth having: it answers
"is anything completely untested" without claiming a number means quality.

**Why hooks at all, given the gates already exist.** A hook is not a second gate; it is the
same gate moved earlier. `check` still decides. What the hook buys is that a formatting or
static-analysis failure is found before it is pushed, which is the difference between an
amended commit and a red pipeline.

**Why authentication is not in this ADR.** Verifying a JWT is production code, and this
repository's rule is that production code arrives behind a failing test and a specification.
It is a decided intention, not an installed dependency, and it gets its own ADR in the
increment that implements it. `notes.md` carries the debt.

## Consequences

- Four new dependencies (`actuator`, two micrometer registries) and two Gradle plugins'
  worth of configuration. Each is named here, so a future reader can ask why any single one
  exists.
- `/actuator/prometheus` is exposed. Which endpoints are public in production is a
  deployment concern and not settled by this ADR.
- Hooks are opt-in per developer: lefthook has to be installed. A hook nobody installed is
  not a gate, which is precisely why `check` remains the gate.
