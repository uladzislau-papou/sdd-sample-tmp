# ADR 0010 – JVM 17 Baseline

## Status
Accepted (inherited from template)

Supersedes ADR-0006.

## Context

ADR-0006 pinned Java 25 and pinned the toolchain vendor, because Gradle's auto-detection
would otherwise match any locally installed JDK reporting language version 25 — including
early-access builds — making the selected JDK depend on the machine.

The template targets Kotlin on the JVM, and the donor service it takes its stack from runs
JVM 17.

## Decision

- **JVM 17** is the baseline. Kotlin `jvmTarget = JVM_17`, Gradle toolchain
  `languageVersion = 17`.
- The toolchain remains the **single authoritative declaration** of the Java version. That
  part of ADR-0006 is kept, not superseded.
- The **vendor pin is dropped**.

## Rationale

**Why 17, stated honestly: this is a downgrade.** The repository already ran Java 25, which
is itself an LTS release. Moving to 17 costs edits to the toolchain, `.sdkmanrc`, the
version catalog, the wrapper and the CI pin, and it gives up virtual threads (21+). The
reason is alignment with the service the stack was taken from, and the fact that JVM 17 is
the oldest baseline Spring Boot 4 accepts — so a service built from this template runs
anywhere a Boot 4 service runs.

It is recorded as a trade-off rather than an improvement so that nobody later mistakes it
for one. A service free to choose should consider 21 for virtual threads.

**Why the vendor pin is dropped.** ADR-0006's reason was specific to Java 25: at the time,
early-access builds reported the same language version as the release, so auto-detection
could pick one. JVM 17 has been LTS for years and that ambiguity is gone. Keeping the pin
would force a second JDK download on machines that already have a matching one — which is
exactly what happened on the first machine this template was built on, where only Zulu 17
was installed.

## Consequences

- Virtual threads are unavailable. A service that wants them raises the baseline and writes
  its own ADR superseding this one.
- The example builds on a stock JDK 17, with no provisioning step. That mattered
  immediately: the previous stack could not be compiled at all on the machine this template
  was authored on, because the jOOQ Gradle plugin refuses to resolve below Java 21.
