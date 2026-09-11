# ADR 0006 – JVM and Kotlin Baseline

## Status
Accepted

## Context

A language and runtime version is not a detail that can be left to whatever a
developer happens to have installed. It decides which features may be used, which
bytecode is produced, and — as the ArchUnit case below shows — whether some tools
can read the output at all.

Three versions need pinning and they are not independent:

- the **JVM toolchain** Gradle compiles and tests with,
- the **`jvmTarget`** the Kotlin compiler emits for,
- the **Kotlin language version**.

A mismatch between the first two produces class files the runtime rejects at load
time rather than at build time, which is the worst place to find out.

## Decision

- **JVM 17 (LTS)**, vendor **Temurin (Adoptium)**, declared as a Gradle toolchain
  in `build.gradle.kts`.
- **`jvmTarget = 17`**, matching the toolchain.
- **Kotlin 2.3**, with `allWarningsAsErrors = true` and `-Xjsr305=strict`.

**The Gradle toolchain block is the single authoritative declaration.** Everything
else is derived from it and must agree:

| Derived artefact | Must state |
|------------------|-----------|
| `.sdkmanrc` | `java=17.0.x-tem` |
| `.github/workflows/build.yml` | `java-version: '17'`, `distribution: 'temurin'` |
| IDE project settings | JDK 17 |

**The vendor is pinned deliberately.** Without `vendor = JvmVendorSpec.ADOPTIUM`,
Gradle's auto-detection matches *any* locally installed JDK reporting language
version 17 — including an early-access or vendor-patched build — so the selected
JDK depends on the machine. The foojay-resolver plugin in `settings.gradle.kts`
provisions Temurin when it is absent, so pinning costs a download rather than a
manual install.

**The baseline tracks LTS releases only.** Moving it is an ADR trigger
(`sdd.playbook.md` § 6 item 13).

## Rationale

### Why 17 rather than 21 or 25

Spring Boot 4 supports 17 as its minimum, and `risk-management-service` — the
service this stack is aligned with (ADR 0001) — runs 17. Running a different
baseline from the neighbouring service would mean two toolchains in one landscape
for no gain that has been articulated.

The features a later LTS would add are substantially less relevant in Kotlin than
in Java. Records, sealed types, pattern matching and virtual threads are the usual
arguments for moving up; Kotlin has had its own form of the first three since
before 17, and nothing in this system is thread-bound. The case for 21 is real but
weak, and it can be made later as its own ADR with a reason rather than now on
general principle.

### Why LTS only

A non-LTS release stops receiving patches in six months. A reference implementation
that requires a JDK nobody should still be running is a reference implementation
nobody can run.

### Why `allWarningsAsErrors`

Because the alternative is a warning count. A project that emits three warnings
emits thirty a year later, and by then nobody reads the ones that matter — a
deprecation scheduled for removal reads exactly like one that is cosmetic.

The cost is real and should be stated: a Spring Boot or Kotlin minor upgrade that
deprecates something **will fail the build**, and the fix cannot be deferred. That
is the intended behaviour. What it must not become is a reason to reach for
`@Suppress` — `coding-style.definition.md` § 9 requires a suppression to carry a
reason, and "to get the build green" is not one.

### Why `-Xjsr305=strict`

Kotlin's null-safety stops at the Java boundary. An unannotated Java return value
arrives as a *platform type* (`T!`), which the compiler permits to be assigned to
both `T` and `T?` without complaint — so a null from Spring or Hibernate can reach
a non-null Kotlin property and fail somewhere else entirely.

`-Xjsr305=strict` makes JSR-305 nullability annotations (which Spring applies
extensively) hard types instead of platform types. That is what makes
`coding-style.definition.md` § 1.4 — "absence is `T?`, `!!` is forbidden in the
core" — an enforceable rule rather than an aspiration, because without it the
compiler has no opinion at the exact boundary where nulls enter.

It is not complete cover: Hibernate and plain JDBC surfaces are not fully annotated,
which is why § 1.4 additionally requires platform types to be narrowed at the
adapter boundary rather than carried inward.

## Consequences

- The toolchain is a build property, not an environment property. A contributor with
  a different JDK installed still builds against 17, because Gradle provisions it.
- Any tool that reads bytecode must be able to read this baseline's class file
  version. **ArchUnit is the live example**: a version whose bundled ASM cannot parse
  the class file format imports **zero** classes, silently, and every architecture
  rule then passes vacuously. `ContextRegistryTest.importer_findsProductionClasses`
  asserts the import size for exactly this reason, and a future toolchain bump must
  re-verify it — a green architecture suite is not evidence that ArchUnit read
  anything (`technical.spec.md`, Testing).
- Raising the baseline touches five files (toolchain, `.sdkmanrc`, CI, and any
  version-sensitive tool) and requires re-verifying the point above. That friction is
  intentional; it is what stops the baseline drifting by accident.
- No preview or experimental language features. Enabling one changes the artifact's
  compatibility guarantees and is a separate ADR.
