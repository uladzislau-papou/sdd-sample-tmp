# ADR 0007 – ArchUnit Boundary Enforcement

## Status
Accepted

## Context

`architecture.definition.md` § 6 lists seven dependency rules and calls them
"Enforceable". § 11 calls the bounded-context registry "a checkable fact rather
than a matter of opinion". Neither claim is true unless something checks.

The alternative to a checker is human attention, and human attention has a known
failure profile for this class of rule: the violations are boring, they are
invisible in a diff that looks reasonable, and they accumulate in exactly the
places nobody re-reads. An import statement added to satisfy a compile error is not
a thing a reviewer notices.

This stack makes the problem worse in three specific ways:

1. **JPA wants to be the domain model.** A single `@Entity` on an aggregate would
   end the framework-free claim, and it would look like a convenience rather than
   like a violation.
2. **Spring Data wants to own the repository interface.** Making
   `MasterLeasingContractJpaRepository` the outport would put
   `org.springframework.data` in `core.outport`, which is § 6 rule 6, and it is the
   path of least resistance.
3. **Two contexts with mutual inport dependencies** (ADR 0003) mean the rule is not
   "no cross-context imports" but "inport only, from a driver only". That is a rule
   nobody holds in their head correctly under time pressure.

## Decision

Add **ArchUnit** as a test-scope dependency and encode the enforceable rules as
tests in `src/test/kotlin/com/jobradleasing/contractmanagement/architecture/`.

In scope:

1. **§ 6 dependency rules** — all seven, one test each.
2. **§ 11 context registry** — top-level packages are exactly `masterleasing`,
   `individualleasing`, `shared`, `bootstrap`; no cross-context import except
   `core.inport`; only a `inbound.driver` class may make that call; `shared`
   imports no context.
3. **Framework containment** — the rules this stack most needs:
   - no `jakarta.persistence` import anywhere in `core` or in
     `shared.domain`/`shared.outport`
   - no `org.springframework` import in the same packages
   - no `graphql.*` or `org.springframework.graphql` import outside `inbound.graphql`
   - no `java.util.Optional` in any `core` or `shared` signature
     (`coding-style.definition.md` § 1.4)
4. **Class roles** (`coding-style.definition.md` § 4.1) — GraphQL mapping
   annotations only in `inbound.graphql`; naming conventions for `*Command`,
   `*Result`, `*UseCase`, `*Driver`, `*Input`, `*Payload`, `*Entity`.
5. **Domain purity** — no `Instant.now()`, `LocalDate.now()` or `LocalDateTime.now()`
   outside `SystemClockPort` (§ 8); no aggregate calling a repository.
6. **Identity boundaries** (ADR 0005) — no context imports another context's
   identity type.
7. **Shared-kernel placement** (§ 9) — adapters for `shared.outport` live in
   `shared.outbound`, not inside a context.

Explicitly out of scope, and stated so the tests are not mistaken for full coverage:

- **Anemic-model detection.** Whether logic sits on the aggregate or leaked into a
  driver is a judgement about intent, not a structural fact. It stays with
  `ddd-hex-reviewer`.
- **Spec/code reconciliation.** Not expressible as an import rule. It stays with
  `spec-documenter`.
- **Doctrine-lands-first** (`file-usage.definition.md` § 5.1). A commit-ordering
  property, not a code property.
- **Bean-lookup coupling.** If `individualleasing` obtained a bean from
  `MasterLeasingConfig`, no import would cross. The § 9 placement rule above only
  catches the *placement* that makes it possible.
- **Whether a returned aggregate is genuinely detached** (§ 4.6). That is runtime
  behaviour; `test.definition.md` § 2.3 assigns it to the persistence suite.

## Rationale

### Why add this when `ddd-hex-reviewer` already checks the same rules

Because they fail differently, and the difference matters.

The reviewer is probabilistic: it reads what it decides to read, and it can be wrong
in both directions. ArchUnit is deterministic — given the same tree it returns the
same answer, and it cannot be talked out of it.

The reviewer is also *invoked*. Someone has to remember. ArchUnit runs on every
`./gradlew test`, including in CI, including for a contributor who has never read
`architecture.definition.md`. That last case is the one that matters for a reference
project: the rules should hold for people who have not internalised them.

They are complementary rather than redundant. ArchUnit covers what is mechanically
checkable; the reviewer covers what needs judgement. Neither subsumes the other, and
the boundary between them is drawn explicitly above.

### Why the framework-containment rules are the highest-value ones here

In a jOOQ or plain-JDBC codebase, "no persistence types in the core" is a rule you
would have to go out of your way to break. With JPA on the classpath it is one
annotation and an IDE quick-fix away, and the result compiles, passes every test,
and looks like ordinary Spring code.

The same applies to `Optional`: Spring Data's `findById` returns one, and letting it
through into a port is a one-character omission that nothing else would catch.

These are the rules where a deterministic check is worth most, because they are the
ones a reasonable person breaks by accident.

### Why "only a driver may make a cross-context call" is a test rather than a convention

ADR 0003 permits `individualleasing` → `masterleasing.core.inport` and the reverse.
Stated that way, the permission is easy to over-apply: a GraphQL controller resolving
a field by calling the other context's inport would satisfy "inport only" and
scatter the coupling across the delivery surface. The narrower rule — orchestration
lives in `inbound.driver` (§ 4.4) — is the one that actually protects the boundary,
and it is expressible as an ArchUnit rule almost verbatim.

### Why not a custom check, or grep

grep checks one rule, has no notion of a package boundary, and reports line matches
rather than class names. ArchUnit expresses layered-architecture rules directly and
its failures read as assertions.

### Why test scope rather than a separate module

The project is a single module (`technical.spec.md`, Build Tool). A separate
architecture module would be more isolated but adds a Gradle subproject to a
one-module build for no gain at this size.

## Consequences

Positive:

- The seven § 6 rules become facts about the build rather than claims in a document.
- Adding a fifth top-level package fails the build, which is the strongest possible
  statement of § 11.
- New contributors get the rules enforced without reading them.
- `ddd-hex-reviewer` gets cheaper: it can trust the structural layer and spend its
  attention on judgement calls.

Negative / accepted trade-offs:

- A new test dependency, hence this ADR (`sdd.playbook.md` § 6 item 2).
- ArchUnit tests are slower than unit tests — they scan bytecode. Acceptable at this
  size; worth watching if the suite grows.
- A rule encoded in ArchUnit and also written in prose is duplication under
  `file-usage.definition.md` § 4. Resolved by making the test the *enforcement* and
  the definition the *statement*: each test cites the section it enforces, and the
  definition stays authoritative. If they ever disagree, the definition wins and the
  test is wrong.
- Structural rules cannot express intent, so a determined author can satisfy every
  rule and still produce a bad design. These tests raise the floor; they do not raise
  the ceiling.
- **A silent import failure makes every rule pass.** See ADR 0006's consequences and
  `ContextRegistryTest.importer_findsProductionClasses`. This is the failure mode
  that makes an architecture suite actively dangerous rather than merely absent,
  because it produces a green tick.

## Future Considerations

- **The "no new top-level package" test is a tripwire, not a gate.** When a third
  context is legitimately added, the test must be updated in the same commit as the
  § 11 table — and per `file-usage.definition.md` § 5.1, the doctrine commit comes
  first.
- Freezing violations with `FreezingArchRule` is deliberately not used. There are no
  violations to freeze, and a freeze store is a way to make rules permanently
  optional.
- If the reviewer and ArchUnit ever disagree about the same rule, that is a finding
  about the rule's wording, not a tie to be broken.
