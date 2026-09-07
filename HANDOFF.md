# Handoff — for the next session

**Non-authoritative.** This ranks below everything in `CLAUDE.md`'s authority order. When
this file and a document in `documentation/` disagree, that document wins and this one is
stale. Deliberately at the repository root rather than in `documentation/`, because
`file-naming.definition.md` governs that folder's taxonomy and a handoff note fits none of
its kinds.

Read `README.md` first for what the repository is. This file is about **where the work
stands and what not to re-decide.**

---

## 1. State in one paragraph

This was Alpine Booking, a Java 25 / jOOQ / H2 reference implementation of Spec-Driven
Development by Dominik Galler. It is now a **Kotlin service template** carrying a small
working example. The process — two nested loops, the document ontology, the review agents —
carried over intact. The stack, the architecture profile, the example and the ADR set were
rebuilt. Zero `.java` remain.

**Verified:** `./gradlew clean build -x integrationTest` green. 123 tests in 20 classes.
ktlint, detekt (both type-resolution variants), bootJar. `initService` proven end to end on
a throwaway copy.

**Not verified:** see § 6. Read it before claiming anything is done.

---

## 2. Do not re-decide these

31 decisions were taken deliberately, each with the alternatives weighed. Reopening one
costs a session and usually lands in the same place. If you genuinely disagree, the way to
change one is an ADR, not an edit.

| # | Decision | The reason it went that way |
|---|----------|------------------------------|
| Stack | Kotlin 2.3, JVM 17, Gradle 8.14, Spring Boot 4, PostgreSQL, REST + GraphQL | Matches a real service in production. JVM 17 is a **downgrade** from what the repo ran, chosen knowingly (ADR-0010) |
| `risk-management-service` | **Stack donor only.** The template is not applied to it | It is brownfield, layered, 884 Kotlin files, with `@Entity` in `domain/model`. A hexagonal gate there would fail on everything and be switched off |
| Transformation | In place, in this repository | Keeping a living example is what stops the definitions decaying into slogans |
| Example size | 4 use cases, 2 bounded contexts | Two contexts are needed for the cross-context rules to have anything to check. One context makes `ContextRegistryTest` decoration |
| Persistence | Template fixes **one rule** — no persistence annotations in the domain. ORM is a project choice | A technology ban is the weaker control; the donor permitted JPA, had no rule, and the annotations reached the domain |
| Database | PostgreSQL only, Testcontainers for `*IT`, no in-memory anything | H2's dialect diverges; a migration proven on H2 has proved something about H2 |
| API contracts | `api/` holding `.http` and `.graphql`, one per transport a use case uses | A folder named after one transport left the other's contract nowhere to live |
| Identity | Derived, never written twice. `initService` renames a copy | Three tests once repeated the package root as a literal; a rename would have left three stale copies that still passed |
| Registry | `architecture.definition.md` § 11 is **parsed** by a test | It was previously copied — prose plus a string literal — so adding a context meant editing both, and the test would keep passing against the stale list |
| ADRs | Two tiers: baseline (inherited) and example (leaves with the tour domain) | ADR-0003 could not be deleted: nine files cite it, including ADR-0005, whose text is immutable |
| Review | Four axes, **split authority**: architecture and conformance block; logic and security report | A blocking gate that cries wolf gets switched off *entirely*, taking the reliable axes with it |
| `/spec-create` | Read-only against Jira and Confluence. No comments, no pages, no subtasks | The write scopes exist. A skill that comments on every run makes the ticket unusable in a week |
| Reviewers | Four agents, never one with a mode flag | Invention leaves a claim to read; omission leaves nothing and is found only by walking a list. One prompt cannot hold both |
| No `--fix` | Ever | It contradicts "reviewer never edits" and "no production code without a failing test", and makes the reviewer the acceptor of its own changes |
| Provenance | Personal reuse. **Not** an Innowise standard | No licence file on the original, which by default means all rights reserved |

---

## 3. The idea the whole repository is built on

**A rule with no executable owner does not survive contact with a refactor.**

Every rule class names its enforcer in `coding-style.definition.md` § 9. Two of the tests
read the *documentation* rather than the code:

- `ContextRegistryTest` — parses § 11's registry table. Restyling that table breaks the
  build; the format contract is written beside it.
- `SpecCitationsTest` — every `SomeTest.some_method` cited anywhere in `documentation/`
  names a test that exists.
- `DocumentationLinksTest` — every ADR reference resolves.

When you find a rule that has rotted, **move it up a row** in that table. Do not restate it
more firmly. That instruction is ADR-0014.

---

## 4. Traps that cost time here

Written down because each one was discovered the expensive way.

**`:app:detekt` is not the gate.** `check` depends on `detektMain` and `detektTest`, the
type-resolution variants, which find things the convenience task does not. Run
`./gradlew check`, or at minimum `detektMain detektTest`.

**Spring Boot 4 split its test-slice annotations into per-module artifacts.** `@DataJpaTest`
needs `spring-boot-data-jpa-test`; `@AutoConfigureTestDatabase` needs
`spring-boot-jdbc-test`. Neither comes from `spring-boot-starter-test`. The failure mode is
an unresolved import, not a helpful message.

**detekt is pinned to `2.0.0-alpha.2`** and its config schema has already moved from 1.x.
`complexity > LongParameterList > constructorThreshold` does not exist, and detekt **fails
the build on an unknown property** rather than ignoring it. Verify a key before adding it.

**Two detekt rules contradict each other on the Testcontainers base class.**
`UtilityClassWithPublicConstructor` wants the constructor non-public;
`AbstractClassCanBeInterface` then wants the class to be an interface, which it cannot be —
Kotlin forbids `@JvmStatic` in an interface companion, and the container must be static.
The second is suppressed with that reason next to it. Do not "fix" it.

**A GraphQL schema must declare a `Query` root**, even with nothing to read. The example's
root carries one infrastructure field, `apiVersion`, labelled in three places as not a
domain read. Do not turn it into a domain query without an ADR — the template has no read
side by decision.

**Gradle build-script helper functions must live inside `doLast`.** Top-level functions in
`build.gradle.kts` cannot be serialised into the configuration cache; the task *runs* and
then fails after doing its work. `initService` was written wrong once this way.

**The `test` / `integrationTest` split is intentional.** `test` needs nothing installed;
`integrationTest` needs Docker; `check` depends on both. Do not merge them.

**JVM 17 is not an accident.** It is the only JDK on the authoring machine, and the previous
stack could not be compiled there at all — the jOOQ Gradle plugin refuses to resolve below
Java 21. Recorded in ADR-0010 as a trade-off so nobody "fixes" it upward silently.

---

## 5. Map

| Looking for | Go to |
|-------------|-------|
| what the repository is | `README.md` |
| authority order, agent roster, slash commands | `CLAUDE.md` |
| the example's vision and its **honest non-goals** | `documentation/project.definition.md` |
| the blank a new service fills in | `documentation/project.definition.template.md` |
| package ontology, dependency rules, **the context registry (§ 11, parsed)** | `documentation/architecture.definition.md` |
| Kotlin style, class roles, **§ 9 enforcement table** | `documentation/coding-style.definition.md` |
| stack, persistence, migrations, gate commands | `documentation/technical.spec.md` |
| test taxonomy, **canonical quality gates (§ 7)** | `documentation/test.definition.md` |
| **canonical ADR triggers (§ 6)** | `documentation/sdd.playbook.md` |
| which ADRs you inherit vs. which leave with the example | `documentation/adr/README.md` |
| inner loop, per increment | `documentation/execution.playbook.md` |
| outer loop, per use case | `documentation/loop.playbook.md` |
| **live debts, updated** | `documentation/notes.md` |
| this effort's plan and its deviation log | `plan.md`, `tasks.md` |

Three lists are single-sourced and must not be copied: ADR triggers, quality gates, the
context registry. `CLAUDE.md` says which lives where.

---

## 6. Open work, in priority order

Detail in `documentation/notes.md`, which is the file to keep updated — not this one.

1. **Run the integration tests.** `docker info && ./gradlew integrationTest`.
   `TourBookingJpaRepositoryIT` and `GuideTourJpaRepositoryIT` compile, are wired, and have
   **never executed**. They are the only unverified code. Expect real findings: with
   `ddl-auto: validate` they are the only check that the Flyway migrations and the JPA
   mappings agree.
2. **Dispatch `ddd-hex-reviewer` and `spec-documenter`** on the full tree. Neither has run.
   The architecture claims currently rest on 27 passing ArchUnit rules and not on the
   adversarial review meant to sit above them.
3. **Commit, then perform step 8.3** — replace `plan.md` and `tasks.md` with
   `plan.template.md` and `tasks.template.md`. It was deferred because at that point nothing
   was committed, so deleting them would have destroyed the deviation record rather than
   moving it into history.
4. **Decide on detekt.** Keep the alpha, or wait for a stable release. Every generated
   service inherits the choice.
5. **Add an integration test for `findConfirmedByTourId`.** Exercised only through mocks
   today. A status predicate is exactly the kind of thing that behaves differently against a
   real database. Recorded as a gap in
   `documentation/ports/tour-booking-repository.outport.spec.md` § 3.
6. **Exercise `/spec-create` and `/code-review` on a real ticket.** Both are written and
   neither has run. `/code-review`'s security axis cannot be exercised on this example at
   all — no auth, no PII, no outbound calls.

---

## 7. How to work here

```shell
docker compose -f docker-compose/docker-compose.yaml up -d   # PostgreSQL
./gradlew test              # fast: domain, use case, slice. No Docker
./gradlew integrationTest   # every *IT, real PostgreSQL. Needs Docker
./gradlew build             # everything, plus ktlint and detekt
./gradlew bootRun
```

The loops, in the order they are meant to be used:

```
/spec-create <TICKET>   → use-case spec(s), decomposition confirmed first
/uc-to-plan <ucNN>      → plan.md
/plan-to-task           → tasks.md
/execute-task <N.M>     → one task block, TDD-first
/loop-uc <UCNN>         → outer loop until the DoD is met
/code-review            → four axes, split authority
```

Non-negotiable, from `CLAUDE.md`: **no implementation without a spec, no architectural
change without an ADR, no production code without a failing test.**

---

## 8. Two habits worth keeping

**Verify empirically instead of assuming.** Whether Spring for GraphQL finds
`@MutationMapping` on an interface was ambiguous in its own documentation, so the pair was
written and the slice test was allowed to decide. Whether the registry gate actually fires
was proven by adding an unregistered package and watching it fail. A gate nobody has seen
fail is a gate nobody knows works.

**Record what you did not do.** Every unverified item in § 6 is written down somewhere that
outranks a chat message. An absence nobody wrote down reads as verified — and that is how
66 stale test citations survived a commit with every box ticked and every gate green.
