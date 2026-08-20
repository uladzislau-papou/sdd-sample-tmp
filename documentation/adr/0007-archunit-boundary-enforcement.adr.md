# ADR 0007 – ArchUnit Boundary Enforcement

## Status
Accepted

Confirmed by the maintainer. `sdd.playbook.md` § 6 item 2 (new external dependency)
fired and implementation waited for this confirmation, per `execution.playbook.md`
§ 3.2.4.

## Context

`architecture.definition.md` § 6 lists seven dependency rules and calls them
"Enforceable". § 1 has said since March that "dependency rules are explicit so they can
be enforced (e.g., via ArchUnit)". No such enforcement exists. The only boundary check
ever committed was a `grep` in a throwaway task list.

The cost of that gap is now measured rather than hypothetical. Three § 6 violations
survived five months in a repository whose entire premise is architectural discipline:

- `RequestTourBookingUseCase` and `ChangeParticipantsUseCase` imported
  `core.outport.AvailabilityUnavailableException`, against § 4.2.
- `BookingExceptionHandler` imported from `core.outport`, against § 6 rule 3.
- `LoggingDomainEventPublisher` and `SystemClockPort` implemented `shared.outport` from
  inside `booking`, wired by `BookingConfig`, so `guide` depended on `booking`'s
  configuration.

None was subtle. All three were found the first time an agent was pointed at the rules
with instructions to check them — and `ddd-hex-reviewer` found them by reading imports,
which is exactly what a machine does faster, cheaper and without getting bored.

A planted-violation test made the same point from the other direction: an HTTP annotation
moved from `TourBookingRestAPI` onto `TourBookingController` silently relocated a route
and broke four tests. A structural rule would have failed the build immediately.

## Decision

Add **ArchUnit** as a test-scope dependency and encode the enforceable rules as tests in
`app/src/test/.../architecture/`.

In scope:

1. **§ 6 dependency rules** — all seven, one test each.
2. **§ 11 context registry** — top-level packages are exactly `booking`, `bootstrap`,
   `guide`, `shared`; no `booking` ↔ `guide` import; `shared` imports no context.
3. **Class roles** (§ 4.5, `coding-style.definition.md` § 3.3) — HTTP annotations only on
   `*RestAPI`, never on `*Controller`; naming conventions for `*Command`, `*Result`,
   `*UseCase`, `*Driver`, `*Request`, `*Response`.
4. **Domain purity** — no Spring/Jakarta/Jackson/jOOQ import in any `core` or in
   `shared.domain`/`shared.outport`; no `Instant.now()`, `LocalDate.now()` or `new Date()`
   outside `SystemClockPort` (§ 8).
5. **Identity boundaries** (ADR 0005) — no context imports another context's identity type.
6. **Shared-kernel placement** (§ 9) — adapters for `shared.outport` live in
   `shared.outbound`, not inside a context.

Explicitly out of scope, and stated so the tests are not mistaken for full coverage:

- **Anemic-model detection.** Whether logic sits on the aggregate or leaked into a driver
  is a judgement about intent, not a structural fact. It stays with `ddd-hex-reviewer`.
- **Spec/code reconciliation.** Whether a spec describes the code is not expressible as an
  import rule. It stays with `spec-documenter`.
- **Doctrine-lands-first** (`file-usage.definition.md` § 5.1). A commit-ordering property,
  not a code property.
- **Bean-lookup coupling.** The A4 violation crossed no import; `guide` obtained a bean
  from `BookingConfig`. ArchUnit would not have caught it, and the § 9 rule above only
  catches the *placement* that made it possible.

## Rationale

### Why add this when `ddd-hex-reviewer` already checks the same rules?

Because they fail differently, and the difference matters.

The reviewer is probabilistic: it reads what it decides to read, and it can be wrong in
both directions. It has been — it reported a "known gap" in `GuideTourJooqRepository`
that did not exist. ArchUnit is deterministic: given the same tree it returns the same
answer, and it cannot be talked out of it.

The reviewer is also *invoked*. Someone has to remember. ArchUnit runs on every
`./gradlew test`, including in CI, including for a contributor who has never read
`architecture.definition.md`. That last case is the one that matters for a reference
project — the rules should hold for people who have not internalised them.

They are complementary rather than redundant: ArchUnit covers what is mechanically
checkable and the reviewer covers what needs judgement. Neither subsumes the other, and
the boundary between them is drawn explicitly above.

### Why now, and why after the fixes rather than before?

After, deliberately. Landing ArchUnit while A1/A2/A4 were open would produce a red build
and immediate pressure to weaken the rules to get green — the exact failure mode
`file-usage.definition.md` § 5.1 warns about for doctrine changes. The violations were
fixed first (commits `9f14103`, `7e7a6c2`), so these tests start green and every future
failure is a genuine regression.

### Why not a custom check, or the existing grep approach?

The grep in the old `tasks.md` checked one rule, ran once, and was deleted with the file.
ArchUnit expresses layered-architecture rules directly, reports violations with class
names rather than line matches, and its failures read as assertions rather than as shell
output.

### Why test scope rather than a separate module?

The project is a single `app` module. A separate architecture module would be more
isolated but adds a Gradle subproject to a two-file build for no gain at this size.

## Consequences

Positive:

- The seven § 6 rules become facts about the build rather than claims in a document.
- Adding a fifth top-level package fails the build, which is the strongest possible
  statement of § 11.
- New contributors get the rules enforced without reading them.
- `ddd-hex-reviewer` gets cheaper: it can trust the structural layer and spend its
  attention on judgement calls.

Negative / accepted trade-offs:

- A new test dependency, hence this ADR.
- ArchUnit tests are slower than unit tests — they scan bytecode. Acceptable at this size;
  worth watching if the suite grows.
- A rule encoded in ArchUnit and also written in prose is duplication under
  `file-usage.definition.md` § 4. Resolved by making the test the *enforcement* and the
  definition the *statement*: each test cites the section it enforces, and the definition
  stays authoritative. If they ever disagree, the definition wins and the test is wrong.
- Structural rules cannot express intent, so a determined author can satisfy every rule
  and still produce a bad design. These tests raise the floor; they do not raise the
  ceiling.

## Future Considerations

- **A "no new top-level package" test is a tripwire, not a gate.** When a fifth context is
  legitimately added, the test must be updated in the same commit as the § 11 table — and
  per § 5.1, the doctrine commit comes first.
- Freezing violations with `FreezingArchRule` is deliberately not used. There are no
  violations to freeze, and a freeze store is a way to make rules permanently optional.
- If the reviewer and ArchUnit ever disagree about the same rule, that is a finding about
  the rule's wording, not a tie to be broken.
