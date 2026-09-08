# Handoff — for the next session

**Non-authoritative.** This ranks below everything in `CLAUDE.md`'s authority order. When
this file and a document in `documentation/` disagree, that document wins and this one is
stale. Deliberately at the repository root rather than in `documentation/`, because
`file-naming.definition.md` governs that folder's taxonomy and a handoff note fits none of
its kinds.

Read `README.md` first for what the repository is. This file is about **where the work
stands and what not to re-decide.**

Two phases are recorded here. **Phase 1** turned a Java reference implementation into a
Kotlin service template. **Phase 2** — the current one — is turning that template into the
JRL **Contract Management** service. Phase 1's sections are kept because its decisions are
still load-bearing; where phase 2 overrode one, the row says so.

---

## 0. Read this before touching anything

**The work is uncommitted.** `HEAD` is `db0fda3` on branch `jrl/version-1.0.1`, and phase 2
lives entirely in the **git index**: 130 files, +2276 / −1490, about 101 of them renames.

It was committed once, as eight commits, and then **soft-reset at the user's request** so
they could review the whole change as one diff. Nothing was lost. But a next session that
runs `git checkout`, `git stash` or a hard reset without thinking will destroy a session's
work that no commit protects.

**The user commits. Not the agent.** Stated explicitly after those eight commits: do not
commit without being asked, even when a plan that was approved contains a step named
"commit". Approval of a plan is not approval of each irreversible act inside it.

Two smaller facts about the working tree:

- `java_pid97375.hprof` — a **354 MB** JVM heap dump, produced by a crash during a Gradle
  run this session. Untracked, deliberately not staged, not deleted (that is the user's
  call). `*.hprof` is not in `.gitignore` and probably should be.
- `plan.md` and `tasks.md` are reset from their templates and hold nothing.

---

## 1. State in one paragraph

This was Alpine Booking, a Java 25 / jOOQ / H2 reference implementation of Spec-Driven
Development by Dominik Galler. Phase 1 made it a **Kotlin service template** carrying a
working tour-booking example. Phase 2 is making it the **Contract Management service** for
JRL: the identity, the vision document, the profile documents and the ADR set are now this
service's, eight new ADRs record the decisions, and the first specification —
`uc07-create-master-leasing-contract.spec.md` — exists and is **blocked on eleven questions
the JCM project has not answered**. The tour example is still in the tree, deliberately.

**Verified this session, by measurement rather than assumption:**

- `./gradlew check` green on a clean run: **20 classes / 123 unit tests**, **8 integration
  tests** against real PostgreSQL, `detektMain`, `detektTest`, `spotlessCheck`.
- The two integration tests **had never executed** in phase 1. They do now and they pass —
  so the Flyway migrations and the JPA mappings do agree under `ddl-auto: validate`. Phase
  1's handoff expected real findings there. There were none.

**Not verified:** see § 7.

---

## 2. Do not re-decide these — phase 1

Each was taken deliberately with the alternatives weighed. Reopening one costs a session
and usually lands in the same place. If you genuinely disagree, the way to change one is an
ADR, not an edit.

| # | Decision | The reason it went that way |
|---|----------|------------------------------|
| Stack | Kotlin 2.3, JVM 17, Gradle 8.14, Spring Boot 4, PostgreSQL | Matches a real service in production. JVM 17 is a **downgrade** from what the repo ran, chosen knowingly (ADR-0010) |
| `risk-management-service` | **Stack donor only.** Its architecture is not adopted | Brownfield, layered, 884 Kotlin files, `@Entity` in `domain/model`. A hexagonal gate there would fail on everything and be switched off |
| Transformation | In place, in this repository | Keeping a living example is what stops the definitions decaying into slogans |
| Persistence | One rule — no persistence annotations in the domain. ORM is a project choice | A technology ban is the weaker control; the donor permitted JPA, had no rule, and the annotations reached the domain |
| Database | PostgreSQL only, Testcontainers for `*IT`, no in-memory anything | H2's dialect diverges; a migration proven on H2 has proved something about H2 |
| API contracts | `api/` holding `.http` and `.graphql`, one per transport a use case uses | A folder named after one transport left the other's contract nowhere to live |
| Identity | Derived, never written twice | Three tests once repeated the package root as a literal; a rename would have left three stale copies that still passed. **Phase 2 verified this holds**: renaming the root twice touched nothing in `documentation/`, `api/` or the GraphQL schemas |
| Registry | `architecture.definition.md` § 11 is **parsed** by a test | It was previously copied — prose plus a string literal — so adding a context meant editing both, and the test would keep passing against the stale list |
| Review | Four axes, **split authority**: architecture and conformance block; logic and security report | A blocking gate that cries wolf gets switched off *entirely*, taking the reliable axes with it |
| `/spec-create` | Read-only against Jira and Confluence | The write scopes exist. A skill that comments on every run makes the ticket unusable in a week |
| Reviewers | Four agents, never one with a mode flag | Invention leaves a claim to read; omission leaves nothing and is found only by walking a list. One prompt cannot hold both |
| No `--fix` | Ever | It contradicts "reviewer never edits" and "no production code without a failing test" |
| Provenance | Personal reuse. **Not** an Innowise standard | No licence file on the original, which by default means all rights reserved. See § 8 |

Two phase-1 decisions were **overridden** in phase 2 and should not be reinstated by
inertia:

| Phase-1 decision | What replaced it |
|---|---|
| Two transports, REST and GraphQL, both live | GraphQL only for the MVP (ADR-0020). REST is still *supported*; it just has no consumer yet |
| The repository is a template that generates services | It **is** the service (Q18). `initService` and `project.definition.template.md` are gone; the template survives as a snapshot on `main`, with no back-porting promise |

---

## 3. Do not re-decide these — phase 2

Twenty decisions were taken in a structured interview before any code moved. They are in
force. Where one has an ADR, the ADR is the authority and the row is a pointer.

| # | Decision | Recorded in |
|---|----------|-------------|
| 1 | This repository becomes the Contract Management service; rms supplies the stack | `README.md`, `project.definition.md` |
| 2 | The tour example stays until the first CM context is complete end to end, then leaves in one commit with ADR-0003 → `Withdrawn` | `project.definition.md`, `adr/README.md` |
| 3 | Hexagonal architecture stays; from rms come the stack and the operational mechanics, not the package structure | `technical.spec.md` |
| 4 | Two bounded contexts, `mlc` and `ilc`, split by contract level | **ADR-0015** |
| 5 | Domain vocabulary is English, with the translation table and the cost stated | `coding-style.definition.md` § 4.3 |
| 6 | Specifications are derived from Confluence pages in the `JCM` space, not from Jira | `project.definition.md`, `uc07` § 1 |
| 7 | Personal remote for now; the licence question is a tripwire, not a task | § 8 below, `notes.md` |
| 8 | One deployable for the MVP, though the component view draws two services | **ADR-0016** |
| 9 | Package root `com.example.contractmanagement`, entry point `ContractManagementApplication` | `build.gradle.kts`, `architecture.definition.md` § 3 |
| 10 | `mlc` / `ilc`; `MasterLeasingContract` / `IndividualLeasingContract` | **ADR-0015** |
| 11 | The contract is ours; its participants are external, behind an anti-corruption layer | **ADR-0017** |
| 12 | Lifecycle transitions are aggregate methods; no state-machine framework | **ADR-0018** |
| 13 | Outbox, observability, lefthook, Makefile, JWT intent; **not** S3, webhooks, playwright, audit-as-a-feature; jacoco reports and never blocks | **ADR-0019**, **ADR-0021** |
| 14 | GraphQL is the only transport; REST and springdoc arrive with the first machine consumer | **ADR-0020** |
| 15 | Run the integration tests first; skip the review agents on the departing example | done; § 7 |
| 16 | Order: create LRV → create ELV with its link → terminate LRV | `notes.md`, § 7 |
| 17 | ADR ledger 0015–0020 plus the nine inherited baseline ADRs; 0003 withdraws with the example | `adr/README.md` |
| 18 | Service only; the template freezes as a snapshot on `main` | `README.md` |
| 19 | The inbound port is described from the domain's needs; a GraphQL mutation is its first adapter; AGO's format is not guessed at | `uc07` § 1 |
| 20 | Specifications are written in English, without German quotations | `project.definition.md` |

**Decision 9 changed twice.** It was first `com.jobradleasing.contractmanagementservice`,
matching the donor service, and the user then chose the neutral
`com.example.contractmanagement`. `rootProject.name` and `spring.application.name` remain
`contract-management-service` — they name the artifact and the running service, not its
owner. If you rename the root a third time, note that the rename is mechanical precisely
because identity is derived (§ 2).

Two ADRs were written **after** the interview, because the work demanded them:

- **ADR-0021** — the operational baseline. `sdd.playbook.md` § 6 makes a new dependency, new
  infrastructure and a new cross-cutting concern all ADR-requiring, and observability is all
  three, so it could not arrive as a quiet edit to `build.gradle.kts`.
- **ADR-0022** — how the outbox row is written. See § 5, because *how it was found* matters
  more than the decision.

---

## 4. The idea the whole repository is built on

**A rule with no executable owner does not survive contact with a refactor.**

Every rule class names its enforcer in `coding-style.definition.md` § 9. Three of the tests
read the *documentation* rather than the code:

- `ContextRegistryTest` — parses § 11's registry table, **in both directions**. A package
  with no row and a row with no package both fail. Restyling that table breaks the build;
  the format contract is written beside it.
- `SpecCitationsTest` — every `` `SomeTest.some_method` `` cited anywhere in
  `documentation/` names a test that exists. **Its regex matches `Class.method` only**, so a
  citation naming a class alone is invisible to it. That is why `uc07` § 10 says in words
  that its behaviour items are not yet checkable.
- `DocumentationLinksTest` — every ADR reference resolves.

When you find a rule that has rotted, **move it up a row** in that table. Do not restate it
more firmly. That instruction is ADR-0014.

---

## 5. What phase 2 learned, and it is one lesson twice

**A summary is not a source.**

ADR-0019 was written asserting that the outbox row is written in the same transaction while
citing ADR-0002 for "after-commit publication". Those cannot both hold: ADR-0002's reference
adapter delivers via `@TransactionalEventListener(AFTER_COMMIT)`, so an appender subscribed
that way writes the row *after* the commit and loses the atomicity the outbox exists for.

The defect entered through `adr/README.md`'s one-line summary of ADR-0002 — *"Domain events
are published after commit"* — which collapses **where publication is called** and **when
delivery happens** into one clause. ADR-0002's actual decision is "the driver publishes
within the `@Transactional` boundary via the `DomainEventPublisher` outport", which was
always compatible with an outbox; its own Future Considerations even name the substitution.
ADR-0019 was written against the index, not against the ADR.

Resolved by **ADR-0022**: the adapter behind `DomainEventPublisher` writes the row
synchronously in the ambient transaction, a separate scheduled relay dispatches, the driver
and the domain do not change, and ADR-0002's status stays `Accepted` because its decision
was never in conflict — only its reference adapter was. The index's summaries now describe
decisions in the ADR's own terms.

The same failure class produced the three single-sourced lists in `CLAUDE.md` and produced
`SpecCitationsTest`. It found a third victim this session and will find a fourth.

---

## 6. Traps that cost time here

Phase 1's, still true:

**`detekt` is not the gate.** `check` depends on `detektMain` and `detektTest`, the
type-resolution variants, which find things the convenience task does not. The lefthook
pre-commit hook runs the correct pair for the same reason.

**Spring Boot 4 split its test-slice annotations into per-module artifacts.** `@DataJpaTest`
needs `spring-boot-data-jpa-test`; `@AutoConfigureTestDatabase` needs `spring-boot-jdbc-test`.
The failure mode is an unresolved import, not a helpful message.

**detekt is pinned to `2.0.0-alpha.2`** and its config schema has moved from 1.x. It **fails
the build on an unknown property** rather than ignoring it. Verify a key before adding it.
Phase 2 closed the open question about keeping the alpha: rms runs the same one, and this is
no longer a template whose choice every generated service inherits.

**Two detekt rules contradict each other on the Testcontainers base class.** The suppression
carries its reason. Do not "fix" it.

**A GraphQL schema must declare a `Query` root**, even with nothing to read. The example's
root carries one infrastructure field, `apiVersion`. Turning it into a domain query needs
the read-side ADR — the service has no read side, and that absence is now a written debt.

**Gradle build-script helper functions must live inside `doLast`.** Top-level functions
cannot be serialised into the configuration cache.

**The `test` / `integrationTest` split is intentional.** `test` needs nothing installed;
`integrationTest` needs Docker; `check` depends on both.

Phase 2's, each found the expensive way:

**Documentation was not an input to the `test` task.** The three documentation-reading gates
therefore did not run on documentation-only changes — `test` stayed `UP-TO-DATE` and the
tests written to catch documentation rot were silent on exactly the commits they existed
for. Fixed by declaring `documentation/` and `api/` as inputs, and verified the way this
repository verifies gates: by touching a document and watching the task stop being
up-to-date. **If you add another folder the tests read, add it there too.**

**`git checkout -- <file>` on an unstaged rewrite destroys it.** A whole rewritten
`notes.md` was lost to a `checkout` intended to revert one appended newline. Stage first, or
revert precisely.

**A `FAILED` from `detektMain` can be a stale Gradle daemon.** One occurred immediately
after a plugin was added, passed on re-run, and later evidence — a 354 MB heap dump — says a
JVM had died. Re-run once on a clean daemon before believing a sudden static-analysis
failure; and if you find a heap dump, do not let `git add -A` sweep it in.

**Adding a bounded context is not a `mkdir`.** § 11 is parsed both ways, so the row and the
package must land in the same increment. `mlc` and `ilc` are decided and **not yet
registered**, on purpose.

---

## 7. Open work, in priority order

`documentation/notes.md` is the file to keep updated — not this one. It carries the eleven
questions in the form they should be *asked*, grouped by who can answer them.

1. **Read the Miro board** linked from the `Contract Management Domain Data model` page.
   Relationships, cardinalities and **optionality** are the natural content of a model board,
   and optionality is `uc07`'s primary blocker. Two of the eleven questions may already be
   answered there. It is outside the Confluence scopes available to the agent; a human must
   open it.
2. **Get the eleven questions answered.** Seven are for the product owner and the discovery
   team — mandatory fields, DLV linkage timing, affiliated contracts, one-active-contract-
   per-employer, the status list, who sets `activation_date`, the field-level validity
   matrix. Four are for the architecture owners — who invokes creation and what fills
   `owner`, who owns the employer record and whether we verify it, `MLC` or `MLA`, one
   deployable or two. Until then `uc07` cannot be implemented and `/loop-uc` must not be
   started on it.
3. **Four items that are ours, not theirs.** A GraphQL row in
   `architecture.definition.md` § 8.1's timestamp table, and an ArchUnit rule keyed to it —
   the table names `inbound.rest` and GraphQL is now the only transport. An ADR for a second
   shared-kernel identity, which `adr/0005` reserves. A `BLOCKED` status in
   `use-case.spec.template.md` plus a `/loop-uc` preflight gate, because `uc07`'s blocked
   state is legible to a human and invisible to the loop. And GraphQL in the template's
   Bounded Context vocabulary.
4. **Dispatch `ddd-hex-reviewer`, `conformance-reviewer` and `spec-documenter`.** None has
   ever run. `spec-reviewer` has — four rounds on `uc07`, finding real defects in every one,
   including an unsourced actor, an invented validation rule filed as sourced, and an open
   question referenced four times and never written. The other three will behave the same
   way on their first real subject, so plan for findings rather than a rubber stamp.
5. **The read-side ADR**, before the first display use case. The MVP is substantially about
   displays and this repository has no query side at all.
6. **Authentication**, as its own increment with a spec and a failing test. It was
   deliberately excluded from ADR-0021 because verifying a JWT is production code. Every
   GraphQL operation is unauthenticated today.
7. **Then the queue**: create ELV with its link to the LRV, terminate LRV (blocked on the
   status list), and finally delete the tour example with ADR-0003 → `Withdrawn`.
8. **`/code-review` has never run.** Its security axis cannot be exercised on the tour
   example at all — no auth, no PII, no outbound calls. It becomes meaningful once
   authentication and the Radar/Odoo adapters exist.

---

## 8. Tripwire: provenance

The method here was written by **Dominik Galler**; the original carries no licence file,
which by default means all rights reserved. The justification for this copy is *personal
reuse, not distributed*.

That stops being true on a specific **event**, not on a date: the first push to a repository
owned by the client or an organisation, or the first handover of this code as deliverable
work. Either makes it a distribution. The cheap fix is one message to the author before that
happens.

Recorded as a tripwire rather than a task because there is nothing to do until one of those
two events is imminent. The decision to defer it was the user's, made knowingly.

---

## 9. Map

| Looking for | Go to |
|-------------|-------|
| what the repository is | `README.md` |
| authority order, agent roster, slash commands | `CLAUDE.md` |
| the service's vision and its **honest non-goals** | `documentation/project.definition.md` |
| package ontology, dependency rules, **the context registry (§ 11, parsed)** | `documentation/architecture.definition.md` |
| Kotlin style, class roles, English vocabulary (§ 4.3), **§ 9 enforcement table** | `documentation/coding-style.definition.md` |
| stack, persistence, outbound integration, operational baseline, gate commands | `documentation/technical.spec.md` |
| test taxonomy, **canonical quality gates (§ 7)** | `documentation/test.definition.md` |
| **canonical ADR triggers (§ 6)** | `documentation/sdd.playbook.md` |
| every decision and its tier | `documentation/adr/README.md` |
| inner loop, per increment | `documentation/execution.playbook.md` |
| outer loop, per use case | `documentation/loop.playbook.md` |
| **the eleven questions, phrased to be asked** | `documentation/notes.md` |
| the first Contract Management specification, and why it is blocked | `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` |

Three lists are single-sourced and must not be copied: ADR triggers, quality gates, the
context registry. `CLAUDE.md` says which lives where.

---

## 10. How to work here

```shell
make up                     # PostgreSQL for local development
./gradlew test              # fast: domain, use case, slice. No Docker
./gradlew integrationTest   # every *IT, real PostgreSQL. Needs Docker
./gradlew check             # the gate: both test tasks, spotless, detektMain, detektTest
make setup                  # install the lefthook hooks
```

The loops, in the order they are meant to be used:

```
/spec-create <page|ticket>  → use-case spec(s), decomposition confirmed first
/uc-to-plan <ucNN>          → plan.md
/plan-to-task               → tasks.md
/execute-task <N.M>         → one task block, TDD-first
/loop-uc <UCNN>             → outer loop until the DoD is met
/code-review                → four axes, split authority
```

Non-negotiable, from `CLAUDE.md`: **no implementation without a spec, no architectural
change without an ADR, no production code without a failing test.**

---

## 11. Three habits worth keeping

**Verify empirically instead of assuming.** Whether the registry gate fires was proven by
adding an unregistered package and watching it fail. Whether the documentation gates fire on
a documentation change was proven by touching a document and watching the task stop being
up-to-date. Whether the migrations and the mappings agree was proven by running the
integration tests that had never run. A gate nobody has seen fail is a gate nobody knows
works.

**Record what you did not do.** Every unverified item in § 7 is written down somewhere that
outranks a chat message. An absence nobody wrote down reads as verified — and that is how 66
stale test citations survived a commit with every box ticked and every gate green.

**Let the adversarial reviewer finish.** `spec-reviewer` returned `GAPS` four times on one
specification, and each round found something the previous rewrite had introduced. The
temptation after round two is to declare it good enough; rounds three and four caught a
criterion asserting the timing it claimed not to assert, a count that was wrong by a third,
and an open question referenced four times that did not exist. Stop when what remains needs
a human decision — not when the findings get uncomfortable.
