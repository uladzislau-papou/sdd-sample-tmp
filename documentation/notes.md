# Notes

Non-authoritative scratchpad. See `CLAUDE.md` for the documentation authority order.

---

## Live debts — carried out of the template transformation

Everything else from that effort was either finished or encoded into a document that
outranks this one. These are the items that are genuinely still open, kept here rather than
in `tasks.md` because they outlive it.

### Never executed: the two integration tests

`TourBookingJpaRepositoryIT` and `GuideTourJpaRepositoryIT` compile, are wired, and have
**never run**. The machine the template was authored on had no Docker daemon, so
`./gradlew integrationTest` could not start a container.

They are the only unverified code in the repository. The other 123 tests pass.

First thing to do with Docker available:

```shell
docker info && ./gradlew integrationTest
```

Expect real findings there and not a formality — those tests are the only check that the
Flyway migrations and the JPA mappings agree, because `ddl-auto: validate` makes Hibernate
refuse to start when they do not.

### Never dispatched: `ddd-hex-reviewer` and `spec-documenter`

`test.definition.md` § 7 gate 12 requires a `PASS` on the architecture axis, and
`tasks.md`'s Definition of Done required `spec-documenter` to report no gaps. Neither agent
was run during the transformation.

So the architecture claims rest on the 27 ArchUnit rules, which do pass, and not on the
adversarial review that is supposed to sit on top of them. The rules catch what they were
written to catch; the reviewer exists because rules do not catch everything.

### Written but never exercised: two axes of `/code-review`

`conformance-reviewer` and the security axis have never been run against a real increment.

The security axis in particular cannot be exercised here at all: the example has no
authentication, no PII and no outbound calls, so there is nothing for it to find. It first
does real work in a real service. Recorded in `project.definition.md`'s non-goals as well.

### No integration test for `findConfirmedByTourId`

Exercised only through `TourStartedListenerTest`'s mocks. A status predicate is exactly the
kind of thing that behaves differently against a real database. Recorded in
`ports/tour-booking-repository.outport.spec.md` § 3 as a gap rather than a decision.

### Decide before adopting: detekt is pinned to an alpha

`dev.detekt` `2.0.0-alpha.2`, inherited from the donor service, which needs it for Kotlin
2.3 support. It works, but its configuration schema has already moved relative to 1.x — the
key `complexity > LongParameterList > constructorThreshold` exists in 1.x and does not
exist here, and detekt **fails the build on an unknown property** rather than ignoring it.

Every service generated from this template inherits that. Worth a decision: keep the alpha,
or wait for a stable release and accept an older Kotlin.

### Provenance, unresolved

The repository this template was extracted from was written entirely by Dominik Galler and
carries no licence file, which by default means all rights reserved. Scope was narrowed to
personal reuse and this is not distributed. `README.md` records it. Anyone intending to go
further should ask the author first.

### `tasks.md` and `plan.md` still hold the transformation

The transformation's last step was to replace them with `plan.template.md` and
`tasks.template.md`. It was **not** performed, deliberately: at that point nothing had been
committed — `HEAD` was still the pre-transformation commit with 231 files diverged — so
deleting those two files would have destroyed the deviations record rather than moving it
into history.

Commit first, then replace them with the templates. The templates already exist.
