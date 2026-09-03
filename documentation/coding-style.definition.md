# Coding Style Definition (Kotlin) --- AlpineBooking / SDD Reference

## Purpose

This document defines the coding style for the project to ensure: -
consistent readability and maintainability - predictable navigation
(package / class roles) - consistent use of idiomatic Kotlin features
(data classes, sealed hierarchies, null-safety, `when` exhaustiveness) -
consistent KDoc usage suitable for Spec Driven Development (SDD)

Scope: - Production code (domain, application/use-cases, adapters) -
Tests (naming and structure)

Rewritten for the Kotlin migration (`adr/0009-kotlin-migration.adr.md`).
Where a rule changed only in syntax, the original intent is preserved.
Where Kotlin's language guarantees replace something Java needed discipline
for (chiefly null-safety), that is called out explicitly rather than left
implicit — a workshop attendee should be able to see *why* a rule dissolved,
not just that it did.

------------------------------------------------------------------------

## 1. General Principles

### 1.1 Explicit Roles over Cleverness

-   Classes MUST communicate intent by name + package.
-   Prefer small, role-specific types over large "god classes".

### 1.2 Immutability First

-   Domain and DTO structures SHOULD be immutable (`val`, not `var`).
-   Mutation is allowed only at boundaries (e.g., deserialization) and MUST
    be isolated.

### 1.3 Always-Valid

-   Domain objects MUST NOT be constructible in an invalid state.
-   Validation belongs to `init` blocks, factory functions, or value
    objects' primary constructors.

### 1.4 Null Policy

-   Domain and application layer MUST use Kotlin's nullable types (`T?`) to
    express absence, not a wrapper type. Kotlin's compiler enforces the
    check at every call site, which is the language mechanism the Java
    baseline approximated with `Optional<T>` by convention only.
-   `Optional<T>` (or an equivalent hand-rolled wrapper) MUST NOT be used.
    Wrapping a nullable type in another type that can itself be null-checked
    away adds nothing the compiler doesn't already give for free, and it is
    exactly the indirection Kotlin's null-safety exists to remove.
-   At boundaries (e.g., REST), nulls MUST be normalized immediately —
    unchanged from the Java-era rule.

**The Java-era `Optional`-vs-nullable-record-component split no longer
exists as a rule.** Under Java, `Optional<T>` was required for method return
values but forbidden on record components (for the reasons the Java version
of this document gave: allocation overhead, reflection, deconstruction).
That asymmetry was a carve-out around a single type's limitations. In
Kotlin, `T?` is the same construct everywhere — a return type, a
constructor parameter, a property — so there is nothing to carve out.
Absence is still governed by the same two substantive constraints the
Java-era exception listed, and they apply uniformly now rather than only to
record components:

1.  **Absence must be a real business case.** A nullable component or
    return type is legitimate because *not having one* is a real state — a
    caller with no correlation id, a cancellation with no reason given.
    "Not filled in yet" is not a business case; that is a missing invariant
    wearing a null.
2.  **The nullability MUST be documented** with a `@param` or `@property`
    KDoc tag (or prose in the class KDoc) saying what absence means. An
    undocumented nullable property or parameter is a violation of this
    section.

Carried forward from the Java version, where it was recorded because four
production records relied on the exception without the exception being
written down (found by `ddd-hex-reviewer` during the UC08 review). The rule
that provenance protects — nullability must be a documented business
decision, not silence — is unchanged; only the Java-specific carve-out
mechanics are gone.

------------------------------------------------------------------------

## 2. Language Level & Modern Kotlin Policy

Target: Kotlin 2.4.10, JVM 25 (LTS baseline — `adr/0009-kotlin-migration.adr.md`)

### 2.1 data class

Use `data class` when: - the type is a pure data carrier - equality is
structural - it has no lifecycle/state transitions

Data classes MAY validate invariants in an `init` block.

Prefer a data class over a plain `class` with manually written
`equals`/`hashCode`/`toString`/`copy` for any such type — writing those by
hand is exactly the boilerplate `data class` exists to remove, and a
hand-written version is one edit away from silently diverging from the
constructor.

### 2.2 sealed class / sealed interface

Use sealed hierarchies when: - the set of subtypes is closed and
meaningful - you want exhaustive `when` handling

Rules: - Prefer `sealed interface` for role/contract types, `sealed class`
when the subtypes share state or behaviour. - Subtypes SHOULD be declared
`final` (Kotlin's default — no `open` unless extensibility is deliberate).

### 2.3 `when` expressions and smart casts

-   Prefer `when` expressions over cascaded `if`/`else`.
-   A `when` over a sealed hierarchy MUST be exhaustive (no `else` branch
    papering over an unhandled subtype) unless the branch is genuinely
    intentional and documented.
-   Use smart casts (`is` checks) in place of explicit casting where they
    increase readability.

### 2.4 Deterministic floating-point behaviour

-   Not a default concern; this project does no floating-point arithmetic
    in the domain. If a future business requirement introduces one,
    document the determinism requirement via ADR rather than relying on
    default JVM floating-point semantics.

------------------------------------------------------------------------

## 3. Package & Layer Conventions (Hexagonal)

### 3.1 Package naming

-   Base package:
    `com.dominikgaller.<project>.<context>`
-   Packages MUST be lowercase.
-   No generic util dumping ground.

### 3.2 Layer naming

**Defined in `architecture.definition.md` § 3 and § 4. Not restated here.**

The ontology is `core` (`domain` / `inport` / `outport`) · `inbound`
(`driver` / `listener` / `rest`) · `outbound` (`persistence` / `integration`) ·
`bootstrap` · `shared`. Unchanged by the Kotlin migration — the ontology was
never Java-specific.

### 3.3 REST split

-   \*RestAPI = the HTTP contract: an interface carrying all Spring MVC
    annotations
-   \*Controller = the web adapter: implements \*RestAPI, carries no HTTP
    annotations
-   Controllers MUST only map, normalize, delegate, translate errors
-   Controllers depend on `core.inport` interfaces, never on drivers

Note on terminology: `*RestAPI` is **not** the inbound port. The inbound port is
`core.inport.usecase.*UseCase`; `*RestAPI` is a delivery-side interface that exists so
the HTTP contract sits in one place. See `architecture.definition.md` § 4.5.

------------------------------------------------------------------------

## 4. Naming Conventions

### 4.1 Types

-   \*Driver = application service
-   \*RestAPI = inbound contract
-   \*Controller = web adapter
-   \*Repository = outbound port
-   \*Mapper = pure mapping component

### 4.2 Functions

-   Commands MUST be verbs.
-   **Lookup** queries on a repository or port MUST start with `find*`,
    `get*` or `load*` — e.g. `findById`, `findConfirmedByTourId`.
-   **Accessors** are exempt and use the property style: a `status`
    property, not a `getStatus()` method, and no prefix on either.

The exemption is structural, not stylistic: Kotlin properties (and data
class components) are accessed without a `get*` prefix by construction — the
language doesn't offer a `get*`-prefixed accessor for a `val`/`var` the way
Java's JavaBean convention did, so a rule requiring one would forbid the
construct § 2.1 mandates for data carriers. Carried forward unchanged from
the Java-era rule, which existed because roughly forty domain accessors were
found violating an earlier, unqualified "queries MUST start with get*"
wording (found by `ddd-hex-reviewer`, which also flagged its own uncertainty
about whether that wording was ever meant to cover value accessors).

### 4.3 Variables

-   Prefer `val` over `var` for locals and properties; reach for `var` only
    where the value genuinely changes (see § 5.1 for the aggregate-state
    exception).
-   Prefer explicit types in public APIs (function signatures, public
    properties); local type inference is fine where the type is obvious
    from the right-hand side.

------------------------------------------------------------------------

## 5. Modifiers & Structure

### 5.1 Visibility and mutability

-   Default to the most restrictive visibility (`private` before
    `internal` before public).
-   Properties MUST be `val` **except** aggregate and entity state that a
    state-transition method mutates.

The exception is not a concession, it is the point: an aggregate with a
lifecycle cannot have all-`val` state. `TourBooking.status`,
`.participantCount`, `.availableCapacity` and `GuideTour.status`,
`.startedAt` are mutated by `confirm`, `cancel`, `markActive`,
`changeParticipants` and `start`. What the rule protects is preserved by
other means: mutation happens only inside the aggregate, only through named
transition methods that enforce the invariants, backed by a `private set`
so nothing outside the aggregate can assign directly — there are no public
setters (`modelling.definition.md`, `sdd.playbook.md` § 8), which ArchUnit
enforces.

Everything else stays `val`: value objects, data classes, DTOs, collaborator
references in drivers, adapters and configs.

Carried forward from the Java-era rule (originally "fields MUST be private
final", which every aggregate in the project violated as an unqualified
statement — same defect class as the dead layering text and the unused
naming style `test.definition.md` § 5.1 once carried, found by
`ddd-hex-reviewer`).

### 5.2 Constructors

-   Prefer primary-constructor injection (`class Foo(private val bar: Bar)`)
    over field injection or a body-only secondary constructor.
-   No Lombok. Kotlin's primary constructors, data classes and default
    parameter values cover what Lombok's `@Value`/`@RequiredArgsConstructor`
    covered in the Java baseline — carrying a Java annotation-processing
    library into a Kotlin codebase fights the language rather than uses it
    (`adr/0009-kotlin-migration.adr.md`).

------------------------------------------------------------------------

## 6. Error Handling

### 6.1 Application Layer

-   MUST NOT return `null` directly from a function whose return type
    should express absence — use `T?` (§ 1.4).
-   Use explicit exceptions for command failures.

### 6.2 Exception taxonomy

-   NotFoundException -\> HTTP 404
-   ValidationException -\> HTTP 400
-   ConflictException -\> HTTP 409

Do NOT use `IllegalArgumentException` for business semantics.

**The test is where the value comes from, not what validates it upstream.**

- A value object constructed from a **command** — i.e. from data that entered through an
  inport — MUST throw a **domain exception** on an invariant violation. Boundary validation
  is an adapter concern and is **not** part of the core's contract: the same use case may
  later be driven by a message consumer or a scheduler with no Bean Validation in front of
  it, and the value object is then the only guard. The domain may not assume an adapter
  ran.
- A value object constructed from an **internal or infrastructure source**, never from a
  command, MAY throw `IllegalArgumentException` — reaching it means a programmer error.
- A null guard for a parameter typed non-nullable in Kotlin (`T`, not `T?`) is out of
  scope: the compiler rejects passing `null` at every call site within Kotlin code, so no
  runtime guard is needed. The one place a null can still arrive is a boundary crossing
  from outside Kotlin's type system (deserialization, reflection, a Java caller) — that is
  a boundary-normalization concern (§ 1.4), not a value-object concern.

This carries forward the Java-era table (`ParticipantCount`, `ParticipantContact`,
`AvailableCapacity`, `TourId` and their construction sites) unchanged in substance; it is
re-derived per type as each is rebuilt in Kotlin rather than restated here, since the
Java-era table described Java call sites that no longer exist.

### Shared-kernel exemption

A value object in `shared.domain` **cannot** satisfy clause A: it has no domain exception
available to it. `architecture.definition.md` § 9 forbids `shared` from depending on any
bounded context, so a shared value object cannot reference a context-owned exception type —
and this is not a matter of taste, it is enforced (verified by a context-registry
architecture test, e.g. `shared_dependsOnNoBoundedContext`).

The options, carried forward from the Java-era decision:

1. Add a `shared.domain.exception` package with a shared invariant exception. Rejected —
   it grows the shared kernel to serve one blank-string-shaped check, against § 9's "keep
   `shared` minimal", and every context would then have to map a second exception type.
2. Leave `IllegalArgumentException` and map it to 400 at the boundary. **Chosen.**

So `IllegalArgumentException` → 400 is mapped in both `*ExceptionHandler`s. That mapping is
a backstop for shared-kernel value objects and for boundary-crossing identifiers (e.g. a
raw `UUID.fromString` on a path variable), and it is **not** a licence for a context-owned
value object to skip clause A. Those have a domain exception available; they must use it.

------------------------------------------------------------------------

## 7. KDoc Standard

### 7.1 Required

-   public types in domain, application, in, out
-   public functions on ports and drivers

### 7.2 Template --- Type

```kotlin
/**
 * One sentence purpose.
 *
 * Details about invariants and semantics.
 *
 * SDD: Reference to spec.
 */
```

### 7.3 Template --- Function

```kotlin
/**
 * Verb phrase description.
 *
 * @param name Meaning and constraints
 * @return Meaning (never null unless the return type is nullable)
 * @throws Exception when condition
 */
```

------------------------------------------------------------------------

## 8. Boundary Mutation Rules

-   Mutation MAY happen in deserialization/binding layers.
-   MUST rebuild immutable (`val`-backed) domain instances before
    propagation.
-   MUST NOT leak partially mutated domain objects.

------------------------------------------------------------------------

## 9. Code Hygiene

-   Long-living TODOs MUST reference ticket or ADR.
-   Use interfaces in signatures, concrete types in construction.
-   Prefer Kotlin's collection/sequence operators (`map`, `filter`, `fold`,
    …) when they clarify intent; reach for `Sequence` instead of eager
    collection operators when the pipeline is large enough that eager
    intermediate allocation matters.

------------------------------------------------------------------------

## 10. Enforcement

Recommended: - Spotless with the ktlint integration (formatter + style) -
ArchUnit for dependency rules — unaffected by the language change, since it
operates on compiled bytecode rather than source

------------------------------------------------------------------------

End of document.
