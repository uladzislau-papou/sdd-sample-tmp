# Tasks — Template transformation

Derived from `plan.md`. Replaces the previous `tasks.md`, which tracked Alpine
Booking's remaining feature work and is preserved in git history.

**Governance for this effort is deliberately split** (`plan.md` Phase 4 rationale):

- The Kotlin port of the vertical slice is production code. It follows
  `tdd.definition.md` — tests port first, RED before GREEN.
- Document, ADR, `.claude/`, build and CI edits are scaffolding. They run as a
  flat ordered checklist. No use-case spec is written for "turn this repository
  into a template": it has no actor, no aggregate, no command and no
  transaction boundary, and filling the ten sections of
  `use-case.spec.template.md` would require inventing all ten.

---

## DoD Scoreboard — Plan

Mirrored from `plan.md` § "Definition of Done for this plan". The plan is
authoritative; this is a scoreboard.

- [ ] `publish-showcase.yml` gone; no workflow can push outside this repository
- [ ] `./gradlew clean test build` green on JVM 17 / Gradle 8.14
- [ ] Zero `.java` files under `app/src`
- [ ] Three ArchUnit tests green, none containing a hardcoded package literal
- [ ] `ContextRegistryTest` derives its context list from `architecture.definition.md` § 11
- [ ] `./gradlew initService` produces a compiling, green service
- [ ] UC01 reachable over both REST and GraphQL, with both `api/` files
- [ ] UC06 has no `api/` file and its spec says so explicitly
- [ ] UC02 makes `CONFIRMED` reachable, so UC06's happy path is reachable end to end
- [ ] No document outside the example's own specs names a tour, a booking or a guide
- [ ] `ddd-hex-reviewer` returns `PASS` on the full tree
- [ ] `spec-documenter` reports no gaps and no unresolved conflicts
- [ ] Four agents and six slash commands registered in `CLAUDE.md`, single-sourced
- [ ] `plan.md` and `tasks.md` replaced by empty `*.template.md`

---

## Phase 0 — Remove the external risk

- [x] **0.1** Delete `.github/workflows/publish-showcase.yml`
- [x] **0.2** Confirm no other workflow, script or git remote targets a repository outside this one

## Phase 1 — Delete what does not survive

- [x] **1.1** Delete the use-case specs that go: UC02, UC03, UC04, UC07, UC08, UC09, UC10, UC11, UC12. Keep UC01, UC05, UC06 and `use-case.spec.template.md`
- [x] **1.2** Delete the port specs that go. Keep the two inport specs for UC01/UC05, both repository outports, `domain-event-publisher.outport.spec.md`, `clock.outport.spec.md`
- [x] **1.3** Keep both aggregate specs (`TourBooking`, `GuideTour`) and `domain.spec.template.md`; prune references to deleted use cases
- [x] **1.4** Delete the production and test sources of the dropped use cases (`Confirm*`, `Cancel*`, `ChangeParticipants*`, `Complete*`, `MarkBookingCompleted*`, `MarkBookingCancelledByGuide*`), including their commands, results, drivers, requests, responses and events
- [x] **1.5** Delete the Flyway migrations for the dropped features; renumber to a clean V1/V2 pair
- [x] **1.6** `git mv rest api`; delete the `.http` files of dropped use cases; keep `uc01` and `uc05`
- [x] **1.7** Remove the jOOQ code-generation chain from `app/build.gradle.kts` (`flywayMigrate` → `jooqCodegen`, the H2 codegen database, the jOOQ version-forcing block) and its entries from `libs.versions.toml`
- [x] **1.8** Delete the empty `documentation/integrations/`; empty `documentation/notes.md` to its heading
- [~] **1.9** ~~Green build after the deletions, still on Java~~ — **unachievable, see Deviations**

## Phase 2 — Move the platform

- [x] **2.1** Gradle wrapper 9.3.1 → 8.14
- [x] **2.2** `.sdkmanrc` → JDK 17; `libs.versions.toml` `java = "17"`; toolchain and Kotlin `jvmTarget = JVM_17`
- [x] **2.3** Add the Kotlin plugins (`jvm`, `plugin.spring`, `plugin.jpa`) and Kotlin stdlib/reflect; keep Java compilation alive during the port
- [x] **2.4** Swap jOOQ for Spring Data JPA; add `spring-boot-starter-data-jpa`, PostgreSQL driver, `flyway-database-postgresql`
- [x] **2.5** Add Testcontainers (`spring-boot-testcontainers`, `testcontainers-postgresql`); remove H2 entirely
- [x] **2.6** Add `docker-compose/docker-compose.yaml` with PostgreSQL for local development; point `application.yml` at it
- [x] **2.7** Point `application-test.yml` at Testcontainers; every `*IT` runs against PostgreSQL
- [x] **2.8** Re-enable the Gradle configuration cache in `gradle.properties` (the Flyway plugin that blocked it is gone)
- [x] **2.9** Add detekt, ktlint and spotless for Kotlin; `allWarningsAsErrors = true`
- [x] **2.10** Update `.github/workflows/build.yml`: JDK 17 pin, new gate commands
- [x] **2.11** `./gradlew clean test build` green on JVM 17 / Gradle 8.14

## Phase 3 — Rename the class roles

- [x] **3.1** `coding-style.definition.md`: `*Controller` → `*RestController`; add the `*GraphQLAPI` / `*GraphQLController` roles
- [x] **3.2** `ClassRoleRulesTest`: rename the REST rules, add the GraphQL role rules
- [~] **3.3** Classes, tests and `architecture.definition.md` done. Port specs and use-case specs still name the old roles — folded into 6.9
- [x] **3.4** `./gradlew clean test build` green

## Phase 4 — Port to Kotlin, tests first

Per slice: port the tests (RED) → port the production code (GREEN) → dispatch
`ddd-hex-reviewer` and `spec-documenter` in parallel → gates.

- [x] **4.1** `shared/`: `TourId`, `DomainEvent`, `TourStarted`, `ClockPort`, `SystemClockPort`, `DomainEventPublisher`, `LoggingDomainEventPublisher`
- [x] **4.2** Slice UC01 `RequestTourBooking` — domain (`TourBooking` aggregate, value objects, exceptions, `TourBookingRequested`)
- [x] **4.3** Slice UC01 — inport (command, result, use case), outport (repository, `AvailabilityChecker`), driver
- [x] **4.3b** Slice UC02 `ConfirmTourBooking` — inport, driver (restored, see Deviations)
- [x] **4.4** Slice UC01 — REST adapter: `TourBookingRestAPI`, `TourBookingRestController`, request/response, exception handler
- [x] **4.5** Slice UC01 — **GraphQL adapter**: `TourBookingGraphQLAPI`, `TourBookingGraphQLController`, SDL in `src/main/resources/graphql/booking/`, `api/uc01-*.graphql`. New code, not a port
- [x] **4.6** Slice UC01 — JPA adapter: `TourBookingJpaEntity` in `outbound/persistence`, mapper, repository. Domain stays unannotated
- [x] **4.7** Slice UC05 `StartTour` — `guide` domain, inport, driver, REST adapter, JPA adapter
- [x] **4.8** Slice UC06 `MarkBookingActive` — inport, driver, `TourStartedListener`. No API, no `api/` file
- [x] **4.9** `bootstrap/`: application class and the three Spring configurations
- [x] **4.10** Port the three ArchUnit tests
- [x] **4.11** Delete `app/src/**/*.java`; confirm zero remain
- [~] **4.12** Fast gates green (117 tests, detekt, ktlint). **`integrationTest` unverified — no Docker on this machine.** `ddd-hex-reviewer` not yet dispatched

## Phase 5 — Remove the hardcoded identity

- [x] **5.1** Drop `scanBasePackages`; the bootstrap class's own package becomes the scan root
- [x] **5.2** One shared test helper derives `ROOT` from the bootstrap class package; remove all three literals
- [x] **5.3** `architecture.definition.md` § 11: prose → strict table, with the document/test contract written beside it
- [x] **5.4** `ContextRegistryTest` parses § 11 and derives its expected context list
- [x] **5.5** Record the procedure: registering a bounded context starts with the document, not the package
- [x] **5.6** `initService` Gradle task — moves package directories, rewrites `package`/`import`, sets `rootProject.name` and `group`, swaps in `project.definition.template.md`
- [x] **5.7** Rename the current identity to a neutral root (not `com.innowise.*`); verify `initService` on a throwaway copy

## Phase 6 — Generalize the documents

- [x] **6.1** `README.md` — what this repository is and how to start a service from it
- [x] **6.2** `project.definition.md` — the example's definition, with explicit non-goals: no read side, no authentication, no PII, no optimistic locking
- [x] **6.3** `project.definition.template.md` — the blank for a new service
- [~] **6.4** `architecture.definition.md`, `coding-style.definition.md` and `technical.spec.md` done; `modelling.definition.md` (14 refs) and `test.definition.md` (12) still carry unlabelled domain examples
- [x] **6.5** `use-case.spec.template.md`: `## 9. REST Contract` → `## 9. API Contract` covering REST and GraphQL; add the § 1 `Source` field
- [x] **6.6** `file-naming.definition.md`: `rest/` → `api/`, both file kinds, the `uc<nn>` rule and max+1 assignment
- [x] **6.7** `test.definition.md` § 7: gate 12 rewritten for `api/`; the review gate written per-axis (architecture and spec conformance block; logic and security are recorded)
- [x] **6.8** Rebuild the ADR set — rewrite ADR-0006 as the JVM 17 baseline, delete ADR-0003 and ADR-0008, add the baseline ADRs (persistence rule, dual API, PostgreSQL/Testcontainers, quality tooling), mark all `Accepted (inherited from template)`
- [x] **6.9** Update the three surviving use-case specs to the new § 9 shape; UC06 states explicitly that § 9 is not applicable
- [~] **6.10** Not performed — `spec-documenter` was never dispatched. Carried to `documentation/notes.md`

## Phase 7 — The two new skills

- [x] **7.1** Agent `spec-reviewer` — opus, `Read`/`Grep`/`Glob` only, no `Write`, no `Bash`. Five rejection rules; hunts claims without a source; `OPEN QUESTION` is a permitted end state
- [x] **7.2** Agent `conformance-reviewer` — sonnet, read plus `Bash` for running tests. Matches § 7 criteria to tests and § 10 boxes to artifacts
- [x] **7.3** Skill `.claude/skills/spec-create/SKILL.md` — Jira/Confluence read-only via MCP; one hop; later comments outrank the description but conflicts become `OPEN QUESTION`; unread attachments listed as `OPEN QUESTION`; decomposition proposed and confirmed before writing; `uc<nn>` max+1; ADR-trigger report; dispatches `spec-reviewer`
- [x] **7.4** Skill `.claude/skills/code-review/SKILL.md` — four axes, split authority, `ReportFindings` for logic and security, no `--fix`, no `--comment`. Delegates architecture to `ddd-hex-reviewer`, conformance to `conformance-reviewer`, logic to `mattpocock-skills:code-review`, security to `security-review`
- [x] **7.5** `loop.playbook.md` and `execution.playbook.md`: Review phase reaches the review skill; the blocking axes are named
- [x] **7.6** `CLAUDE.md`: agent roster 2 → 4, slash commands 4 → 6, `rest/` section rewritten for `api/`. Both lists stay single-sourced
- [x] **7.7** `sdd.playbook.md` § 6: confirm the ADR triggers `/spec-create` reports against are the canonical list, uncopied

## Phase 8 — Close out

- [~] **8.1** `./gradlew clean build -x integrationTest` green — 123 tests in 20 classes, ktlint, detekt (both type-resolution variants), bootJar. **`integrationTest` and both agents not run** — carried to `documentation/notes.md`
- [x] **8.2** Verify `initService` end to end on a throwaway copy: rename, build, tests green
- [~] **8.3** Templates written (`plan.template.md`, `tasks.template.md`). **Replacement deliberately not performed — blocked on a commit.** See Deviations

---

## Deviations from `plan.md`

Recorded as they happened. `plan.md` stays authoritative on intent; these are the places
where reality forced a different route to the same end.

**1.9 dropped — a green Java baseline is unreachable on this machine.** The build needed
Java 21+ (the jOOQ Gradle plugin refuses to resolve below it) and targeted Java 25, while
the only locally installed JDK is Zulu 17 and sdkman is not present. So the pruned Java
tree could never be compiled, let alone verified green. The first verifiable green build
is therefore after Phase 2, not after Phase 1. This also settles Q23 after the fact: JVM
17 was not a downgrade for its own sake, it is what the machine actually has.

**4.11 moved to the front of Phase 4.** Consequence of the above. With jOOQ removed the
remaining Java could not compile, so keeping it around bought nothing and blocked every
build. Deleting all of it first left an empty module that builds green, and each Kotlin
slice now lands on a green baseline instead of into a long red stretch.

**5.7 done early — the neutral identity is already in place.** Package
`com.example.service`, group `com.example`, `rootProject.name = "service-template"`. Doing
this before writing ~35 Kotlin files avoids renaming all of them afterwards. `initService`
(5.6) is still owed.

**5.1 resolved by moving the entry point, not by configuring it.** `ServiceApplication`
now sits in the **root** package rather than in `bootstrap`. That is what makes dropping
`scanBasePackages` possible at all: Spring's default scan root is the application class's
own package, so from `bootstrap` it would have missed every bounded context. Wiring stays
in `bootstrap`. **Owed: `architecture.definition.md` § 4.9 still says the entry point lives
in `bootstrap` — correct it in 6.4.**

**Two files added that the plan did not list.** `.editorconfig` (ktlint code style, line
length, and one disabled rule with its reason) and `config/detekt/detekt.yml` (two
deliberate departures, each with its reason). Both are template-level decisions rather
than local fixes: `SpreadOperator` fires on Spring Boot's idiomatic Kotlin entry point, so
every service built from this template would otherwise open with a suppression on its main
function.

**Gap found, not yet closed.** UC06 `MarkBookingActive` has no inport spec — there never
was one in `documentation/ports/`. Write it in Phase 6.

**Scope note on 1.2.** Only one port spec was actually dropped
(`mark-booking-cancelled-by-guide.inport.spec.md`); the rest all serve surviving use cases,
including `availability-checker.outport.spec.md`, which UC01 needs.

**UC02 restored — the agreed three-use-case example had an unreachable happy path.**
`TourBooking` runs `REQUESTED -> CONFIRMED -> ACTIVE`. UC01 creates `REQUESTED`, UC06
requires `CONFIRMED`, and the only transition between them is UC02, which Phase 1 had
deleted. Unit and driver tests would still have passed — they build state through
`reconstitute` — but no booking created through the API could ever be activated, so one of
the example's three use cases would have shipped with an unreachable happy path. Raised
before writing the aggregate, since the answer decided whether `confirm()` existed at all.
The example is now four use cases (UC01, UC02, UC05, UC06) and the lifecycle is a
connected chain. Cost: one spec, one `api/` file, six Kotlin files, and one inport spec
still owed in Phase 6.

**Detekt is pinned to a 2.0.0-alpha and its config schema has already moved.** The
`complexity > LongParameterList > constructorThreshold` key exists in detekt 1.x and does
not exist in 2.0.0-alpha.2, which fails the build on an unknown property rather than
ignoring it. The alpha was inherited from the donor service, which needs it for Kotlin 2.3
support. It works, but a template pinning an alpha whose configuration keys move between
builds is carrying a maintenance cost that every generated service inherits. **Decide
before Phase 8** whether to keep it or wait for a stable release. Recorded rather than
worked around.

**Two gate findings on the first Kotlin slice were kept, not silenced.** `SpreadOperator`
on Spring Boot's Kotlin entry point became a configured departure with its reason, because
every service hits it. `LongParameterList` on the aggregate became a class-level
suppression with its reason in the KDoc, because an aggregate's parameter count is a
property of its state and the alternatives are a parameter object nobody uses or setters,
which are forbidden.

**`coding-style.definition.md` was rewritten, not renamed.** Task 3.1 asked for a role
rename inside it, but the document was titled "Coding Style Definition (Java)" and built on
`record`, `sealed`, `strictfp` and JavaDoc. Renaming `*Controller` inside a Java style guide
that has to be replaced anyway is wasted work, so 3.1 was merged with its share of 6.4. The
hard-won rules were carried across by meaning rather than by text: § 1.4's `Optional` rule
and its three-clause exception collapse to four lines, because that exception existed only
to work around Java's inability to express a nullable field. Its surviving clause — absence
must be a real business case, and documented — is the one that mattered.

**The GraphQL contract split works — verified empirically, not assumed.** Spring MVC
inherits `@RequestMapping` from an interface, but Spring for GraphQL's docs are ambiguous:
its detector calls `MethodIntrospector.selectMethods`, which does walk interfaces, while a
comment beside that code warns that `@SchemaMapping` must be on the target class rather
than a proxy interface. So the pair was written with the annotations on
`TourBookingGraphQLAPI` and the slice test was allowed to decide. It passes: `@QueryMapping`
and `@MutationMapping` on the interface are found. `coding-style.definition.md` § 3.3 can
therefore state the GraphQL split as a rule rather than an aspiration.

**GraphQL's specification forces a `Query` root onto a service with no read side.** The
first GraphQL slice test failed to boot with `A schema MUST have a 'query' operation
defined` — the schema had only a `Mutation`. The template has no read side by decision (no
query ports, no projections), so inventing a domain query to satisfy the parser would have
shipped a read side that no spec asked for. The root now carries one infrastructure field,
`apiVersion`, labelled in the schema, in the interface KDoc and in `api/uc01-*.graphql` as
not a domain read, with a note telling the first real service to replace it.

**Detekt caught a rule violating the document that had just been written.** The web slice
tests declared their injected collaborators as `lateinit var`, which `coding-style.definition.md`
§ 5.2 — rewritten one step earlier — forbids. It is unavoidable: `@MockitoBean` and
`@Autowired` field overrides do not accept `val`. Rather than suppress the finding, both
sides were corrected: § 5.2 now scopes the rule to production code and names the exception,
and `VarCouldBeVal` is excluded for test sources with the same reason recorded in
`config/detekt/detekt.yml`. A rule and its enforcement disagreeing is worse than either one
being wrong.

**Two test-only Spring configurations are no longer needed.** `WebTestApplication` and
`PersistenceTestApplication` existed because `@WebMvcTest` searches *upwards* for a
`@SpringBootConfiguration` and the entry point sat in `bootstrap`, a sibling of every
context. With the entry point in the root package (see 5.1 above) the upward search finds
it, so both classes are gone rather than ported. A second, unplanned benefit of that move.

**Gradle now has two test tasks, because one of them needs Docker.** `test.definition.md`
already separated fast tests from adapter integration tests by name; the build did not.
`test` runs everything except `*IT` and needs nothing installed; `integrationTest` runs the
`*IT` classes against PostgreSQL via Testcontainers; `check` depends on both so CI cannot
quietly skip either. Without the split, a developer with no Docker gets no fast feedback at
all, and a gate that cannot be run locally is a gate discovered in CI.

**The Testcontainers ITs are written but unverified.** The Docker daemon is not running on
this machine, so `TourBookingJpaRepositoryIT` and `GuideTourJpaRepositoryIT` compile and are
wired but have never executed. They are the only unverified code in the port. Everything
else — 117 tests across domain, use case, web slice, GraphQL slice and architecture — runs
green.

**Spring Boot 4 split its test-slice annotations into per-module artifacts.**
`@DataJpaTest` is not on the classpath from `spring-boot-starter-test`; it needs
`spring-boot-data-jpa-test`, and `@AutoConfigureTestDatabase` needs `spring-boot-jdbc-test`.
Both were added. Worth knowing before writing the first slice test of any kind in a service
from this template — the failure mode is an unresolved import, not a helpful message.

**§ 11 is now parsed, and the gate was proven to fire.** `ContextRegistry` reads the
registry table out of `architecture.definition.md`, and § 11 carries a written format
contract stating the shape the parser depends on. Verified rather than assumed: adding an
unregistered top-level package made the test fail with a message naming the document, and
removing it made the test pass again. A registry gate that is green because it parsed zero
rows is the exact failure this replaces, so the parser also fails loudly on an empty table.

**`architecture.definition.md` § 4.9 corrected, closing the debt from 5.1.** It said the
entry point lives in `bootstrap`; it now records where the entry point actually is, and why
both Spring's component scan and `ArchitectureRoot` depend on that position. All references
to the original author's package are gone from the document.

**Two detekt findings on the last slice, both fixed rather than suppressed.**
`UtilityClassWithPublicConstructor` on the Testcontainers base class was right in substance
— an abstract base only subclasses may construct should not expose a public constructor, so
it became `protected constructor()`. The `VarCouldBeVal` case is recorded above.

**`GuideTourStatus` lost two values.** `FINISHED` and `CANCELLED` belonged to the
complete-tour and cancel-tour use cases, which are not in the example. Keeping them would
force every exhaustive `when` to carry a branch nothing can reach, and
`coding-style.definition.md` § 2.3 forbids the `else` that would otherwise hide it. Recorded
in the enum's KDoc so a service adding those use cases knows to add the states with them.

**The citation check became a test, and it immediately paid for itself twice.** The old
`plan.md` carried a DoD item saying every `Class.method` citation in `documentation/` should
resolve to a test that exists, "(scripted)". It is now `SpecCitationsTest`, because a script
runs when somebody remembers — the failure mode ADR-0007 exists to end.

What it found on first run: **66 of 69 citations across the four use-case specs were dead**,
and the domain and port specs were worse. The Kotlin port renamed nearly every test method
while every DoD box stayed ticked and every other gate stayed green. The specs read as
complete and were describing tests that no longer existed.

It also surfaced four genuine coverage gaps that the rename had hidden, all now closed with
real tests rather than edited citations:

- no driver-level test that an availability failure propagates and saves nothing (UC01 § 8,
  the 502 path)
- no test that activating a `CANCELLED` booking is rejected, at the aggregate or the driver
- no test that a malformed identifier raises `IllegalArgumentException` at its **source**;
  only the 400 at the boundary was asserted, which would pass even if the 400 came from
  somewhere else

Proven to fire: breaking one citation by hand failed the test with a message that says what
to do — update the citation if the test was renamed, write the test if the coverage never
existed, and do not delete the citation to go green.

**Both aggregate specs and both repository port specs were rewritten, not patched.** They
described cancellation, completion, participant changes and two query methods that the
reduced example does not have — roughly 700 lines specifying absent code. Rewritten from
the actual Kotlin, each carrying a "reduced for the template" note naming what was dropped
and where the non-goal is recorded. The load-bearing rationale was carried across rather
than summarised: § 2.3's standing obligation on `update` is still there, now with the
observation that the JPA adapter closes that defect *by construction* while the obligation
stays written down for the next adapter.

**One gap is recorded rather than closed.** `findConfirmedByTourId` has no integration test
of its own — it is exercised only through the listener's unit tests, and a status predicate
is exactly the kind of thing that behaves differently against a real database. Written into
the port spec as a gap, not a decision.

**The ADR set was restructured into two tiers, not pruned to one.** Q7 said delete
ADR-0003, on the correct grounds that "the `guide` context exists" is not a decision a new
service inherits. Deleting it turned out to be impossible without doing damage: nine files
cite it, including `architecture.definition.md` § 11's registry row, `sdd.playbook.md`
pointing at it as *the precedent* for "a new bounded context requires an ADR", and — the
part that settles it — **ADR-0005's own text**, which cannot be edited because an accepted
ADR's text is immutable. Removing it would have left a rule with no worked example, which
is the failure mode this whole effort keeps running into.

So `documentation/adr/README.md` now declares two tiers: **Baseline (inherited from
template)**, ten ADRs that hold for every service, and **Example**, which is ADR-0003 alone
and gets deleted along with the tour domain. That serves Q7's intent — baseline means
universal — without deleting a load-bearing precedent.

**Superseding was done by status transition, not by rewriting.** ADR-0001 and ADR-0006 were
marked superseded by new ADRs 0009 and 0010 rather than edited, because immutability is a
rule this template teaches and breaking it here would have been the loudest possible
example of not meaning it. ADR-0009 states which of ADR-0001's reasons survived and which
were reconsidered — its outright ban on JPA is the one that was, and ADR-0011 argues on what
grounds. ADR-0008 became **WITHDRAWN**, a status introduced for an ADR whose *subject* no
longer exists: nothing replaced it, so "superseded" would have been a lie, and it is kept
because its reasoning is the best worked example here of synchronous cross-context
integration.

Four new baseline ADRs: 0011 (persistence annotations stay out of the domain), 0012 (dual
transports), 0013 (PostgreSQL and Testcontainers), 0014 (every rule class has an executable
owner).

**A second documentation gate was added, and it earned its place immediately.** Rebuilding
the ADR set left exactly one dangling reference — in a file edited minutes earlier by the
same hand that renumbered the ADR. Markdown links never fail, so nothing would have caught
it. `DocumentationLinksTest` now checks every ADR reference in `documentation/` resolves.

**`initService` was verified end to end, not just written.** On a throwaway copy it renamed
`com.example.service` to `com.acme.riskmanagement`, rewrote 102 files, moved the package
directories with no leftovers, set `rootProject.name` and `group`, repointed the datasource
and the compose container, and installed the blank `project.definition.md`. The renamed
service then built green with all 123 tests — including the ArchUnit suite, which derives
its package root from the entry point, and both documentation gates.

One defect found and fixed in the process: the task's helper functions were declared at the
build script's top level, which Gradle cannot serialise into the configuration cache
("cannot serialize Gradle script object references"). The task *ran* and then failed after
doing its work — the worst shape of failure. Helpers are now local to `doLast`.

**`technical.spec.md` was actively wrong and is rewritten.** It still specified jOOQ as
"the only supported SQL access strategy", H2 as the database, and Java 25 — a profile
document describing a stack that no longer exists anywhere in the repository. It now also
records the two constraints that are *not* the project's to change: the persistence rule
from ADR-0011, and `ddl-auto: validate`, which is the single setting that makes every
integration test also a check that the migrations and the entity mapping agree.

**Step 8.3 was not performed, and the plan was wrong to schedule it where it did.** The
final step was to delete `plan.md` and `tasks.md`, replacing them with blank templates, on
the reasoning that a template shipping with somebody's working checklist inside it is
sloppy. That reasoning holds. The scheduling does not.

Nothing had been committed. `HEAD` was still the pre-transformation commit with 231 files
diverged, so "replace" would not have moved this record into git history — it would have
destroyed it. Every deviation above, including the four coverage gaps the citation gate
found and the reason JVM 17 turned out to be the only option on this machine, exists in
exactly one place: this file, uncommitted.

The plan should have made 8.3 depend on a commit. Committing is not mine to decide, so the
templates are written and in place, the replacement is not done, and the live debts have
been moved to `documentation/notes.md` — which survives the replacement — so that performing
8.3 later loses only the narrative and none of the open work.

**Phase 7 is complete: two agents and two skills.** `spec-reviewer` (opus, read-only) hunts
invention in a freshly drafted spec; `conformance-reviewer` (sonnet, read plus `Bash` for
running tests) hunts omission in code against a spec. They are separate agents rather than
one with a mode flag because the two failure modes leave opposite traces — invention leaves
a claim to read, omission leaves nothing and is found only by walking a list and asking what
each item points at. `/spec-create` is read-only against Jira and Confluence; `/code-review`
fans out to four axes with split authority and has neither `--fix` nor `--comment`.

**Two detekt rules were found pulling the same class in opposite directions.**
`UtilityClassWithPublicConstructor` objected that the Testcontainers base class exposed a
public constructor, so it became `protected`; `AbstractClassCanBeInterface` then objected
that a class with no concrete member should be an interface. It cannot be one — the shared
container must be static, and Kotlin forbids `@JvmStatic` in an interface's companion. The
second rule is suppressed with that reason written next to it. Neither rule was wrong about
what it saw; both cannot be satisfied.

Also worth noting how it surfaced: `:app:detekt` passed while `detektMain` and `detektTest`
— the type-resolution variants that `check` actually depends on — failed. Running the
convenience task is not running the gate.
