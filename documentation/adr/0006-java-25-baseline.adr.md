# ADR 0006 – Java 25 Baseline

## Status
Accepted

Supersedes the `### Java 21` decision in
`adr/0001-technical-stack.adr.md`. ADR 0001 remains immutable and unedited;
this ADR replaces that one section of it.

## Context

ADR 0001 chose Java 21 as the project baseline in March, when 21 was the current
LTS. Java 25 is now the LTS release, and 21's successor in that line.

The repository had drifted into disagreeing with itself about which version it
targets:

| Declaration | Value |
|-------------|-------|
| `gradle/libs.versions.toml` → `java` (the Gradle toolchain, authoritative for the build) | `21` |
| `.sdkmanrc` | `25.0.2-open` (since aligned to `25.0.4-tem`) |
| `.idea/misc.xml` `languageLevel` | `JDK_21` |
| `.idea/misc.xml` `project-jdk-name` | `24`, moved to `25` by IntelliJ during the session that produced this ADR |
| `technical.spec.md`, `coding-style.definition.md`, `execution.playbook.md` | Java 21 |

Only the toolchain governs compilation, so the build was correct and green
throughout — but a developer's shell, their IDE and the build did not agree on
what the project compiles against, and the doctrine documented the older of the
two answers.

Toolchain compatibility, verified rather than assumed:

- **Gradle 9.3.1** (the wrapper) supports Java 25 toolchains, and the
  `foojay-resolver-convention` plugin in `settings.gradle.kts` can provision the
  JDK if it is not installed locally.
- **Spring Boot 4.0.1 / Spring Framework 7** support Java 25.
- **jOOQ 3.20.0**, **Flyway 11.11.0** and **H2 2.3.232** are all compatible.
- No dependency in `libs.versions.toml` pins a maximum Java version.

## Decision

The project baseline is **Java 25**.

- `libs.versions.toml` `java = "25"` — the Gradle toolchain, which is the single
  authoritative declaration. Everything else follows it.
- **The toolchain vendor is pinned to Temurin** (`JvmVendorSpec.ADOPTIUM` in
  `app/build.gradle.kts`). Distribution choice is part of the baseline, not an
  environment detail — see *Why pin the vendor* below.
- `technical.spec.md`, `coding-style.definition.md` and
  `execution.playbook.md` § 3.4 are updated to say 25.
- No dependency versions change. This is a language and runtime bump only.
- `.sdkmanrc` and `.idea/misc.xml` are aligned by the developer; they are
  environment declarations, not build inputs, and the toolchain does not consult
  them.

**LTS-only rule:** the baseline tracks LTS releases only. It does not move to a
non-LTS feature release, and moving it is an ADR trigger
(`sdd.playbook.md` § 6 item 13). The next candidate is the LTS after 25.

## Rationale

### Why move at all, when 21 works?

Two reasons, and the second is the real one.

The project is a *reference-grade* showcase (`project.definition.md`). A reference
implementation sitting on the previous LTS teaches the previous LTS. That is a
presentational argument, and on its own it would not justify the churn.

The stronger reason: the repository already disagreed with itself. `.sdkmanrc`
pinned 25, the IDE was on 24-then-25, the build was on 21, and the docs said 21.
That inconsistency is the actual defect — a contributor following `.sdkmanrc` and a
CI job following the toolchain would compile against different language levels. It
has to be resolved in one direction or the other, and 25 is where three of the four
declarations already pointed.

### Why 25 rather than staying on 21 and pinning `.sdkmanrc` back?

Both resolve the inconsistency. Choosing 25 is the better trade because 21 → 25 is
an LTS-to-LTS step with no dependency changes and no source incompatibilities in
this codebase, so the cost is a one-line toolchain change plus doc updates. Pinning
backwards would mean deliberately choosing the older LTS for a greenfield reference
project with no legacy constraint to justify it.

### Which language features does this actually unlock?

Named concretely, because "newer is better" is not a rationale. Between 21 and 25,
the finalised features relevant to *this* codebase are:

- **Flexible constructor bodies** (final in 25) — statements before `this(...)` or
  `super(...)`. Directly useful for the Always-Valid doctrine
  (`modelling.definition.md`): a value object or aggregate can validate arguments
  *before* delegating to a canonical constructor, instead of validating after or
  duplicating checks across constructors.
- **Unnamed variables and patterns** (`_`, final in 22) — cleans up pattern
  switches over domain event types, where the payload is often irrelevant to the
  branch.
- **Stream gatherers** (final in 24) — relevant to the fan-out in
  `TourStartedListener`, which currently filters and maps by hand.
- **Module import declarations** (final in 25) — minor, but reduces import noise.

Explicitly **not** claimed: preview features. Nothing in this project enables
`--enable-preview`, and this ADR does not authorise it. Structured concurrency and
primitive patterns in `switch` remain out of scope until they finalise and a use
case justifies them.

### Why pin the vendor to Temurin?

Two independent reasons.

**Maintenance.** The `java.net` OpenJDK builds ship each new release but do not
maintain older ones — there is no stream of patch updates for an LTS line, so a
project pinned to `25.0.2-open` stays on `25.0.2` and accumulates unpatched CVEs.
Temurin (Eclipse Adoptium) maintains the LTS line, publishing `25.0.x` updates
across its support window. For a baseline that is explicitly LTS-tracking, the
distribution has to be one that actually maintains LTS.

**Reproducibility.** Without a vendor constraint, Gradle's toolchain auto-detection
matches *any* installed JDK reporting language version 25. On the development
machine that was four candidates, including an early-access Loom build — and before
the pin, the build selected `25.0.2-open` while the shell and IDE were on
`25.0.4-tem`. Same language level, so nothing broke, but it is the same
environment-versus-build divergence this ADR exists to end, reappearing one level
down. Pinning the vendor makes the selection a property of the build rather than of
whatever happens to be installed.

Verified after pinning: `compileJava` and `test` both resolve to
`25.0.4-tem`, and class file major version is 69.

`foojay-resolver-convention` (already in `settings.gradle.kts`) provisions Temurin
automatically where it is absent, so the pin does not become a setup burden.

### Why is the toolchain the single authoritative declaration?

Because it is the only one the build reads. `libs.versions.toml` feeds
`java.toolchain.languageVersion`, which determines what `compileJava` and `test`
actually run. `.sdkmanrc` configures a shell, `.idea/misc.xml` configures an IDE;
both can be wrong without the build noticing — which is exactly how this drift
survived. Naming one declaration authoritative means the others are derived, and a
mismatch is a fixable inconsistency rather than an open question about which is
right.

## Consequences

Positive:

- One answer to "what does this project compile against", and it is the current LTS.
- Flexible constructor bodies make the Always-Valid pattern cleaner at exactly the
  places this codebase cares about — value object and aggregate construction.
- No dependency changes, so no transitive risk.

Negative / accepted trade-offs:

- Contributors need a JDK 25. Mitigated by the `foojay-resolver-convention` plugin,
  which provisions toolchains automatically, so a local 25 is a convenience rather
  than a prerequisite.
- The doctrine's "Java 21 features allowed" phrasing has to be revisited each LTS.
  Accepted — it is one line in `execution.playbook.md` § 3.4, and the ADR trigger
  makes the revisit deliberate.
- **Contributors must have, or let Gradle fetch, a Temurin 25.** The vendor pin
  narrows toolchain matching, so an otherwise-adequate Zulu or `-open` 25 will no
  longer satisfy the build and foojay will provision Temurin instead. Accepted: a
  one-off download in exchange for a deterministic toolchain and a maintained LTS
  line. Resolved this way rather than left open because the alternative — auto-detect
  whatever is installed — is what produced the drift in the first place.

## Future Considerations

- **CI must pin the JDK explicitly** rather than inheriting a runner default, so
  the toolchain and the runner cannot diverge the way the toolchain and `.sdkmanrc`
  did. Relevant when CI is introduced (see `plan.md`, Phase 5).
- Adopting flexible constructor bodies in existing value objects is a refactor with
  no behavioural change; it belongs in a normal increment with its own drift review,
  not in this ADR.
- If `--enable-preview` is ever wanted, that is a separate ADR: it changes the
  compatibility guarantees of the artifact, not just its language level.
