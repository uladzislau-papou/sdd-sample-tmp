# ADR 0009 – Kotlin Migration

## Status
Accepted

Supersedes the language-choice portions of `adr/0001-technical-stack.adr.md`
("Java 21") and `adr/0006-java-25-baseline.adr.md` ("Java 25"). Both ADRs
remain immutable and unedited; this ADR replaces that one decision within
each of them, the same pattern ADR 0006 used against ADR 0001.

## Context

The project was implemented in Java through twelve use cases, then had its
`app/src` deleted and its domain code reset to be rebuilt in Kotlin. The
decision to migrate, and to what, was made with the maintainer across a
clarification pass before any file changed; this ADR records the outcome and
the reasoning.

The Java implementation is not being carried forward file-by-file. The
motivation was to demonstrate the project's own SDD/DDD/Hexagonal doctrine in
Kotlin, not to preserve the existing Java source — so the twelve implemented
use cases were deleted rather than transliterated, and are re-implemented
later, one at a time, through the normal `/uc-to-plan` → `/execute-task` →
`/loop-uc` flow. This ADR governs only the language/build/tooling baseline
that re-implementation will target; it does not itself implement anything.

Three triggers from `sdd.playbook.md` § 6 fire simultaneously:

- **#2** — Introducing a new external dependency (the Kotlin Gradle plugins,
  `kotlin-spring`, `jackson-module-kotlin`).
- **#3** — Introducing new infrastructure (ktlint via Spotless).
- **#13** — Raising the language/framework baseline (Java → Kotlin is a
  strict superset of "raising the toolchain").

One ADR covers all three because they are one decision, not three
independent ones.

## Decision

**The project language moves from Java 25 to Kotlin, keeping the existing
JVM 25 (LTS) target.**

- Kotlin **2.4.10** (latest stable at decision time, verified against the
  Gradle Plugin Portal rather than assumed) via `org.jetbrains.kotlin.jvm`.
- **JVM target stays 25.** An earlier draft of this ADR chose JVM 21 as a
  safety margin, reasoning that Kotlin's compiler support for the newest JDK
  feature releases usually lags the JDK itself. The maintainer overruled
  that in favour of keeping the JVM target ADR 0006 already established,
  which this migration does not need to revisit — the migration's problem
  is the source language, not the runtime. This is safe rather than merely
  convenient: Kotlin 2.4.10's `jvmTarget` compiler option lists `JVM_25` as
  a supported value (verified against the Kotlin Gradle plugin
  documentation, not assumed — the same discipline ADR 0006 required of
  itself), so nothing about this choice repeats ADR 0006's original
  "asserted rather than checked" failure mode. Kotlin's own toolchain
  (`kotlin { jvmToolchain { ... } }`) governs the JDK used to *compile*,
  independent of the `jvmTarget` bytecode level, so the two settings need to
  be checked separately — both are 25 here.
- **`org.jetbrains.kotlin.plugin.spring`** (all-open) is required and added
  alongside the JVM plugin: Kotlin classes are `final` by default, and
  Spring's proxying (`@Service`, `@Transactional`, `@Configuration`, …)
  needs them open. This is not optional tooling — without it the existing
  `inbound.driver` / `bootstrap` conventions (§ 4.4, § 4.9) silently stop
  working the first time a `@Transactional` method needs a CGLIB proxy.
- **`jackson-module-kotlin`** is added so Jackson can deserialize Kotlin data
  classes (constructor parameters, default values) instead of requiring a
  no-arg constructor. Spring Boot registers it automatically when present.
- **jOOQ code generation keeps emitting Java**, not Kotlin. Generated code is
  never hand-edited by either language's convention, jOOQ's Java generator
  is the mature, default path, and this keeps the codegen step unchanged
  from the Java baseline — one less variable in a migration that already
  touches the whole source tree.
- **Everything else in `technical.spec.md`'s stack is unchanged**: Spring
  Boot 4.0.1, jOOQ 3.20.0, Flyway 11.11.0, H2 2.3.232, JUnit 5, AssertJ,
  ArchUnit 1.4.1. This migration is a language change, not a framework or
  persistence-strategy change — items 4 (persistence technology) and 7
  (messaging) of the ADR trigger list do **not** fire.
- **Linting**: Spotless's `kotlin { ktlint() }` block replaces the `java {
  removeUnusedImports(); trimTrailingWhitespace(); endWithNewline() }` block.
  Chosen over Detekt for the same reason `spotless` was originally chosen
  over Checkstyle/ErrorProne (`coding-style.definition.md` § 10, pre-Kotlin
  revision) — it is the smallest addition that keeps the "hygiene-only, no
  restyling" posture the project already committed to, reusing the same
  Gradle plugin rather than introducing a second static-analysis tool.
  Detekt remains available to reconsider later; nothing here forecloses it.
- **No Lombok.** The Java baseline permitted Lombok in adapters/application
  (`coding-style.definition.md` § 5.3, pre-Kotlin revision). Kotlin data
  classes, default parameter values and `copy()` cover the same ground
  natively; carrying Lombok forward would fight the language rather than use
  it.

## Rationale

### Why migrate at all

Not part of this ADR's decision — the maintainer's call, made outside the
architectural-decision process this document exists to record. What this
ADR owns is *how*, given that *whether* was already answered.

### Why delete the Java implementation rather than port it

Considered and rejected two alternatives:

1. **Mechanical port, file-by-file**, preserving the same aggregates, use
   cases and tests. Rejected: it treats Kotlin as "Java with different
   syntax," which produces code that doesn't take advantage of what the
   language actually offers (sealed hierarchies, data classes, null-safety
   replacing the `Objects.requireNonNull` guard class entirely — see the
   corresponding `coding-style.definition.md` revision), and it front-loads
   a large, low-value translation exercise before any spec work happens.
2. **Scaffold-only, then stop.** Considered, but the maintainer's direction
   was explicit: delete `app/src` entirely and defer re-implementation to
   later `/loop-uc` runs, not attempt a trivial vertical slice as proof.

The chosen path — delete, keep the SDD framework (`documentation/`,
`.claude/`, `rest/`), rebuild via the existing spec-driven loop — treats the
Java implementation as what it was: a demonstration of the method, not an
asset independent of the method. Rebuilding through `/uc-to-plan` and
`/loop-uc` is itself a second demonstration of the same process this project
exists to showcase (`project.definition.md`), which a mechanical port would
not provide.

### Why JVM 25, not a step back to 21

`adr/0006` moved the Java baseline to 25 specifically because three of four
version declarations in the repo already agreed on 25 and because Java 25's
toolchain compatibility with Spring Boot 4 / jOOQ 3.20 / Flyway 11 / ArchUnit
1.4.1 was individually verified. None of that changes with the source
language: Spring Boot, jOOQ, Flyway and ArchUnit run against JVM bytecode,
which is identical regardless of whether Kotlin or Java produced it. The
only new variable this migration introduces is the Kotlin compiler's own
JVM-target support, which is verified separately above rather than assumed
to inherit ADR 0006's verification.

An earlier draft of this ADR proposed 21 as a more conservative choice,
reasoning from a general pattern (language tooling lagging the newest JDK)
rather than a checked fact about this specific compiler version. That was a
mistake in the same direction ADR 0006 already corrected once: preferring
an unverified default over verifying the actual claim. Once "does Kotlin
2.4.10 support JVM 25" is checked and answered yes, there is no remaining
reason to move backward off the JVM baseline this project already
established and already verified against the rest of the stack — doing so
would only reintroduce the multi-declaration drift ADR 0006 exists to
prevent, this time between the JVM baseline and the Kotlin migration ADR
rather than between `.sdkmanrc` and the toolchain.

### Why not switch persistence or web stack too

Considered — and rejected by explicit maintainer choice, not by default —
replacing jOOQ with a Kotlin-native SQL DSL (e.g. Exposed), or the test
stack with Kotest/MockK. Rejected because the migration's problem is the
*language*, not the *stack*: jOOQ, Flyway, H2, JUnit 5, AssertJ and ArchUnit
all work unchanged from Kotlin, and swapping them would multiply the risk
surface of a single migration for no problem they cause. `technical.spec.md`
and `test.definition.md` therefore change only where Kotlin syntax forces a
change (e.g. KDoc replacing JavaDoc references), not in tooling choice.

## Consequences

Positive:

- One language target, matching what the doctrine documents describe, with
  no repeat of ADR 0006's declaration-drift failure mode (this time there is
  only one source: `gradle/libs.versions.toml`).
- Kotlin's null-safety subsumes the `Objects.requireNonNull` null-guard
  class from `coding-style.definition.md` § 6.2 entirely — not a stylistic
  win, a class of runtime failure the type system now rejects at compile
  time.
- Data classes and sealed hierarchies map directly onto the existing
  Always-Valid / record / sealed-interface doctrine
  (`modelling.definition.md`, `coding-style.definition.md` § 2), so the
  architectural rules in `architecture.definition.md` needed no structural
  change — only vocabulary (record → data class) — confirming those rules
  were never Java-specific to begin with.

Negative / accepted trade-offs:

- The twelve previously-implemented use cases are gone from `app/src` and
  must be re-implemented from their specs. Accepted: the specs
  (`documentation/use-cases/`, `documentation/domain/`, `documentation/ports/`)
  survive untouched, so no domain knowledge is lost — only the Java
  encoding of it.
- `plan.md` and `tasks.md` are reset to reflect no completed implementation
  work, per the same reasoning: they are working scoreboards
  (`sdd.playbook.md` § 4.2), not historical records, and the historical
  Java-era detail remains available in git history.
- Contributors still need a JVM 25 toolchain, same requirement ADR 0006
  already imposed and already mitigated — the `foojay-resolver-convention`
  plugin in `settings.gradle.kts` provisions it automatically. Unlike the
  Java baseline, however, the Gradle *daemon itself* (not only the
  compilation toolchain) must also run on JVM 21+ for the jOOQ codegen
  Gradle plugin to resolve — Gradle's own toolchain provisioning covers
  compilation targets, not the daemon's JVM. A contributor on an older JDK
  needs `./gradlew updateDaemonJvm` (or an equivalent locally-installed
  JDK 21+) before the build will configure at all. This is a Gradle/jOOQ
  plugin constraint, not a Kotlin one, and it exists independently of which
  JVM target this ADR chose.
- `ArchUnit` 1.4.1's Java-25-bytecode-import requirement from
  `technical.spec.md` (major version 69 unreadable by 1.3.0/1.4.0) applies
  unchanged, exactly as it did under the Java baseline — the class files
  Kotlin produces at JVM target 25 carry the same major version. The
  underlying lesson still carries forward regardless: **re-verify the
  ArchUnit import count after any toolchain change, rather than trusting a
  green suite**, including for the first Kotlin `ArchUnitTest` rewritten
  against real Kotlin classes.

## Future Considerations

- Raising the JVM target beyond 25 to a newer LTS is a normal instance of
  `sdd.playbook.md` § 6 item 13, requiring the same verification discipline
  ADR 0006 applied when it first moved to 25 — Kotlin compiler support,
  Spring Boot, jOOQ, Flyway, ArchUnit, all re-checked rather than assumed.
- Detekt was considered and deferred, not rejected. If ktlint's
  formatting-only scope proves insufficient once real Kotlin domain code
  exists (e.g. complexity or code-smell rules), that is a fresh ADR trigger
  (§ 6 item 3), not a silent addition.
- Generating jOOQ code as Kotlin instead of Java was considered and
  deferred for the same reason — revisit only if a concrete pain point with
  the Java-generated interop appears once real repositories are rewritten.
