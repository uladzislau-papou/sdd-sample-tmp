---
type: note
goal: Humans take notes and share them
---

# Notes

Non-authoritative scratchpad. May not contradict formal definitions
(`file-usage.definition.md`).

## Open questions carried over from the adaptation

These were surfaced while re-basing this documentation set onto
`risk-management-service`. None is decided; each is a candidate for an ADR or a cleanup
increment.

### The exception taxonomy lives in `qes`

`DomainException`, `BadUserInputException`, `EntityNotFoundException` and `DomainErrorCode`
sit in `qes/domain/exception/` and are imported ~110 times across `kyc`, `onb` and `boni`.
That is the single largest source of cross-module coupling in the codebase and it is
entirely accidental — `qes` was simply the first module.

Moving them to `common` is mechanical but wide. Registered as a wart in
`architecture.definition.md` § 11.3. Worth its own increment; not worth doing as a side
effect of feature work.

### `api/integration` holds outbound clients

Outbound provider clients live under a package called `api`. The name says "inbound
delivery" and the contents are the opposite. Renaming touches every provider import.
Registered as a wart; consistency currently beats a half-rename.

### `onb` reads `kyc` internals directly

117 imports, including repositories and entities. Accepted and bounded in
`architecture.definition.md` § 11.2 — `onb` exists to project KYC state outward, the
modules ship together, and a facade would add indirection without reducing coupling.

The line that matters is that `onb` must not **write** `kyc` state outside a `kyc` service.
Worth an explicit reviewer check; worth a test if one can be written cheaply.

### Assertion libraries are mixed

~314 `org.junit.jupiter.api.Assertions`, ~111 AssertJ, ~24 `kotlin.test` across 300 test
files. `test.definition.md` § 1.1 rules that new tests use AssertJ and forbids a
bulk-conversion sweep as a side effect. A deliberate conversion increment is a reasonable
thing to want; it is also a large, low-information diff. Undecided.

### No Testcontainers

Integration tests guard on PostgreSQL availability and skip when it is absent
(`onb/integration/PostgresAvailability.kt`). That means a green local suite can hide an
untested query. `test.definition.md` § 1.3 forbids closing a DoD item with a skipped test,
which is the mitigation, not a fix.

Adding Testcontainers is ADR triggers 2 and 3. Whether it is worth the CI time is a real
question, not a formality.

### No spec coverage yet

`documentation/domain/`, `documentation/integrations/` and `documentation/use-cases/`
contain only templates. The deliberate policy (`sdd.playbook.md` § 9) is that specs are
written for what is **touched**, so coverage grows with the work rather than being
back-filled across ~580 production files.

The first few increments will therefore spend proportionally more time in the Spec Phase
than later ones. That is expected, not a sign the process is too heavy.

### `qes/validation/annatation` is misspelled

Registered as a wart. New constraints go in a correctly spelled sibling package rather
than joining the typo.
