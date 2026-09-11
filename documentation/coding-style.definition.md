# Coding Style Definition (Kotlin) — JRL Contract Management / SDD Reference

## Purpose

This document defines the coding style for the project to ensure:

- consistent readability and maintainability
- predictable navigation (package / class roles)
- consistent use of Kotlin's own features (data classes, sealed hierarchies,
  `when` exhaustiveness, nullability) rather than habits carried over from Java
- consistent KDoc usage suitable for Spec Driven Development (SDD)

Scope:

- Production code (domain, application/use-cases, adapters)
- Tests (naming and structure)

------------------------------------------------------------------------

## 1. General Principles

### 1.1 Explicit Roles over Cleverness

-   Classes MUST communicate intent by name + package.
-   Prefer small, role-specific types over large "god classes".
-   Kotlin makes several kinds of cleverness cheap — extension functions on
    domain types, `operator` overloads, `typealias` for a primitive. Cheap is not
    the test. An extension function that encodes a business rule puts that rule
    outside the aggregate that owns it, and no reviewer looking at the aggregate
    will find it.

### 1.2 Immutability First

-   Domain and DTO structures SHOULD be immutable.
-   `val` is the default; `var` needs a reason, and inside `core.domain` the only
    accepted reason is § 5.1's.
-   Mutation is allowed only at boundaries (deserialization, JPA entities) and
    MUST be isolated.

### 1.3 Always-Valid

-   Domain objects MUST NOT be constructible in an invalid state.
-   Validation belongs in `init` blocks, factory methods, and value objects.
-   **A data class's generated `copy` is a hole in this** and must be closed where
    it matters. `copy` bypasses nothing — `init` still runs — but it *does* let a
    caller construct a variant the domain never intended to be constructible
    directly. Where an aggregate or entity should only be derived through a named
    transition, declare the constructor `private` and expose named factories; the
    compiler then also removes `copy` from the public surface.

### 1.4 Null Policy

**`Optional` is not used in this codebase. Absence is `T?`.**

This is the sharpest departure from the Java-based reference this framework came
from, and it is not a matter of taste. In Java, `Optional<T>` exists because the
type system cannot distinguish a nullable reference from a non-nullable one, so a
wrapper has to carry the information. Kotlin's type system carries it directly, at
the cost of nothing: `MasterLeasingContract?` is checked at compile time, forces
the caller to handle absence before dereferencing, allocates nothing, and reads as
the thing it is.

Keeping `Optional` on top of that would mean a type that is *itself* nullable
(`Optional<T>!` from any Java interop boundary), two ways to spell the same idea,
and `Optional.empty()` versus `null` as a live question at every call site.

Rules:

-   The domain and application layers express absence as `T?`.
-   `java.util.Optional` MUST NOT appear in any signature in `core.*` or
    `shared.*`. Where a Spring Data method returns one, the repository
    implementation unwraps it before the value crosses into the core —
    `findById(...).orElse(null)` at the adapter edge, never in a port.
-   Platform types (`T!`) from Java libraries MUST be narrowed at the adapter
    boundary. An unannotated Java return value entering the core untyped defeats
    the whole mechanism, silently.
-   `!!` is forbidden in `core.*` and `shared.*`. It is an assertion the compiler
    was wrong, and in a layer whose premise is that invalid states are
    unconstructible, the honest form is a `require`/`checkNotNull` with a message
    saying which invariant was supposed to guarantee it.

**Nullable properties on events and commands are a real business case, not a
loophole.** An optional cancellation reason, an absent correlation id, a
`terminatedAt` that only the listener path supplies (`architecture.definition.md`
§ 8.1) — each is a legitimate state, and each is declared as a plain nullable
type. Three constraints, which are the whole of the allowance:

1.  **Absence must be a real business case.** "Not filled in yet" is not one; that
    is a missing invariant wearing a null.
2.  **The nullability MUST be documented**, in KDoc on the property or in prose on
    the type, and it MUST say what absence *means*. An undocumented nullable
    property is a violation of this section, not an instance of its allowance.
3.  **An aggregate accessor may still be non-null where the state model makes it
    so.** `cancelledAt` is nullable because a live contract has none; that is the
    state model, not a shortcut.

------------------------------------------------------------------------

## 2. Language Level & Kotlin Policy

Target: Kotlin 2.3 on JVM 17 (`adr/0006-jvm-and-kotlin-baseline.adr.md`).

`allWarningsAsErrors` is on (`build.gradle.kts`). A deprecation warning is a build
failure, deliberately: the alternative is a warning count that grows until nobody
reads it.

### 2.1 data class

Use `data class` when:

- the type is a pure data carrier
- equality is structural
- it has no lifecycle/state transitions

This covers value objects, commands, results, events, GraphQL inputs and payloads.
`init` blocks validate invariants — that is where Always-Valid lives for a value
object.

A **value object wrapping exactly one primitive** still gets a `data class` rather
than a `typealias` or a bare `String`. `@JvmInline value class` is tempting for the
allocation saving and is deliberately not used: it erases to the underlying
primitive at several boundaries, which makes an overload set ambiguous and makes
the type disappear from exactly the reflective and interop surfaces — JPA, Jackson,
GraphQL — where we most want it to be visible. The allocation is not the cost worth
optimising in a contract system.

### 2.2 sealed interface / sealed class

Use a sealed hierarchy when:

- the set of subtypes is closed and meaningful
- exhaustive `when` handling is wanted

Rules:

- Prefer `sealed interface` for role/contract types.
- Subtypes are `data class` or `data object` unless there is a reason otherwise.
- `DomainEvent` is deliberately **not** sealed — see
  `adr/0004-generic-domain-event-publisher-signature.adr.md`. It is a marker that any
  context may implement, and sealing it would put every context's events in one file.

### 2.3 when expressions and exhaustiveness

-   Prefer `when` over cascaded `if/else`.
-   A `when` over an `enum` or sealed type used as an **expression** is checked for
    exhaustiveness by the compiler. Prefer the expression form for exactly that
    reason: adding a state to `MasterLeasingContractStatus` should break the build
    at every place that reasons about states, and a statement-form `when` with an
    `else` branch is how that guarantee gets thrown away.
-   `else ->` on a `when` over a closed type is a smell. It converts a future
    compile error into a future runtime surprise.

### 2.4 Kotlin features deliberately not used

-   **`lateinit`** — a mutable property that throws until initialised is the exact
    shape Always-Valid forbids. Constructor injection makes it unnecessary.
-   **`@JvmInline value class`** for domain values — see § 2.1.
-   **Context receivers / context parameters** — an implicit dependency in scope is
    the opposite of the explicit orientation § 1.1 asks for.
-   **Companion-object mutable state** — hidden global state, untestable.

------------------------------------------------------------------------

## 3. Package & Layer Conventions (Hexagonal)

### 3.1 Package naming

-   Base package: `com.jobradleasing.contractmanagement.<context>`
-   Packages MUST be lowercase, no underscores.
-   No generic `util` dumping ground.

### 3.2 Layer naming

**Defined in `architecture.definition.md` § 3 and § 4. Not restated here.**

The ontology is `core` (`domain` / `inport` / `outport`) · `inbound`
(`driver` / `listener` / `graphql`) · `outbound` (`persistence` / `integration`) ·
`bootstrap` · `shared`.

### 3.3 GraphQL adapter shape

-   The **schema** (`src/main/resources/graphql/<context>/*.graphqls`) is the contract.
-   `*GraphQLController` is the resolver: it carries `@Controller` and the mapping
    annotations, maps input → command, delegates to the inport, maps result → payload.
-   There is **no `*GraphQLAPI` interface.** The REST convention this framework
    inherited put every HTTP annotation on a separate interface so the contract sat in
    one readable place. Schema-first GraphQL already has that place. Adding an
    interface would create a second contract that can drift from the first, and the
    schema is the one the server actually validates against.
-   Controllers MUST only map, normalize, delegate, and let the exception resolver
    translate errors.
-   Controllers depend on `core.inport` interfaces, never on drivers.

Note on terminology: `*GraphQLController` is **not** the inbound port. The inbound
port is `core.inport.usecase.*UseCase`; the controller is a delivery-side adapter.
See `architecture.definition.md` § 4.5.

------------------------------------------------------------------------

## 4. Naming Conventions

### 4.1 Types

-   `*Driver` = application service (use case implementation)
-   `*UseCase` = inbound port
-   `*Command` / `*Result` = inport data carriers
-   `*GraphQLController` = GraphQL resolver
-   `*Input` / `*Payload` = GraphQL argument and result types
-   `*ExceptionResolver` = GraphQL error mapping
-   `*Repository` = outbound port (the interface, in `core.outport`)
-   `*JpaRepository` = the Spring Data interface, in `outbound.persistence.write`
-   `*PersistenceAdapter` = the class implementing the outport, in `outbound.persistence.write`
-   `*Entity` = JPA row mapping, in `outbound.persistence.write`
-   `*Mapper` = pure mapping component (aggregate ↔ entity)

The `*Repository` / `*JpaRepository` / `*PersistenceAdapter` triple is more names
than the Java/jOOQ version needed, and the reason is Spring Data: it wants to own an
interface named after the aggregate and generate its implementation. If that interface
were the outport, the core would depend on `org.springframework.data`
(`architecture.definition.md` § 6 rule 6). Keeping three names keeps the port clean and
makes each file's role unambiguous.

### 4.2 Functions

-   Commands MUST be verbs: `activate`, `cancel`, `amendConfiguration`.
-   **Lookup** functions on a repository or port MUST start with `find`, `get` or
    `load` — e.g. `findById`, `findTerminableByMasterContractId`.
-   **Accessors** are exempt and use property syntax: `status`, `contractId`, `value`.
    Kotlin properties generate their own accessors, and a `get*` prefix on a property
    (`getStatus`) is not idiomatic and reads as Java through a translator.

### 4.3 Variables

-   Prefer type inference for locals when the type is obvious.
-   Declare explicit types on every public API signature. Inference on a public
    return type means a refactor can silently change a published contract.

------------------------------------------------------------------------

## 5. Modifiers & Structure

### 5.1 Visibility and mutability

-   Default to the most restrictive visibility. `internal` is available and
    underused; prefer it over `public` for anything not part of a package's contract.
-   Properties are `val` **except** aggregate and entity state that a state-transition
    method mutates.
-   Backing collections MUST be `private` and exposed as a read-only snapshot, never
    as the live list. Returning `MutableList` from an aggregate hands a caller the
    ability to mutate the aggregate without going through a transition.

The `var` exception is not a concession, it is the point: an aggregate with a
lifecycle cannot have an all-`val` state. `MasterLeasingContract.status`,
`.activationDate`, `.cancelledDate` and `IndividualLeasingContract.status`,
`.terminatedAt` are mutated by `activate`, `cancel`, `terminate`. What the rule
protects is preserved by other means: mutation happens only inside the aggregate,
only through named transition functions that enforce the invariants, and there are
no setters — a `var` in an aggregate is `private set`
(`modelling.definition.md`, `sdd.playbook.md` § 8), a rule ArchUnit enforces.

Everything else stays `val`: value objects, data classes, commands, results, events,
collaborator references in drivers, adapters and configs.

### 5.2 Classes are final by default, and stay that way

Kotlin classes are `final` unless declared `open`. **Do not open a domain class.**
The only legitimate `open` in this codebase is one a framework demands, and no
framework demands anything of `core.domain` — that is what § 4.1 means.

`kotlin("plugin.spring")` opens `@Component`/`@Configuration` classes automatically
so Spring can proxy them. That plugin's reach stops at Spring annotations, which is
why it never touches an aggregate. `kotlin("plugin.jpa")` does the equivalent for
`@Entity`. Both are configured in `build.gradle.kts` and neither is a licence to
annotate a domain class in order to get the behaviour.

### 5.3 Constructors

-   Constructor injection only. No field injection, no `lateinit var` collaborators.
-   Prefer a primary constructor with `val` parameters.
-   Where an aggregate should only be created through named factories, make the
    constructor `private` (§ 1.3).

------------------------------------------------------------------------

## 6. Error Handling

### 6.1 Application Layer

-   Absence is `T?` (§ 1.4).
-   Use explicit exceptions for command failures.
-   Do not use a `Result`-style return for domain failures in this codebase. One
    mechanism, consistently: mixing exceptions and result types means every call site
    has to know which convention the callee chose.

### 6.2 Exception taxonomy

Domain exceptions extend `RuntimeException` and are declared per aggregate in
`core.domain.<aggregate>.exception`. The GraphQL error classification each maps to
is in `architecture.definition.md` § 4.5.

Do NOT use `IllegalArgumentException` for business semantics.

**The test is where the value comes from, not what validates it upstream.**

- A value object constructed from a **command** — i.e. from data that entered through
  an inport — MUST throw a **domain exception** on an invariant violation. Boundary
  validation is an adapter concern and is **not** part of the core's contract: the same
  use case may later be driven by a message consumer or a scheduler with no GraphQL
  schema in front of it, and the value object is then the only guard. The domain may not
  assume an adapter ran.
- A value object constructed from an **internal or infrastructure source**, never from a
  command, MAY throw `IllegalArgumentException` — reaching it means a programmer error.
- Null guards are out of scope; a null in a non-null Kotlin parameter is a programmer
  error or an interop failure, not a business case.

#### Kotlin's `require` is a trap here

`require(...)` throws `IllegalArgumentException` and `check(...)` throws
`IllegalStateException`. They are the idiomatic Kotlin spelling, they read beautifully,
and in a value object constructed from a command **they are the wrong exception** by the
rule above.

So:

```kotlin
// Wrong — IllegalArgumentException for a business rule the caller can violate.
init { require(value.isNotBlank()) { "Cancellation reason must not be blank" } }

// Right — the domain's own exception, which the resolver maps to BAD_REQUEST.
init {
    if (value.isBlank()) {
        throw InvalidMasterLeasingContractException("Cancellation reason must not be blank")
    }
}
```

`require` and `check` remain correct for genuine programmer errors — an adapter
handing a mapper a row it just read, an internal consistency assertion. The
discriminator is § 6.2's, not the function's ergonomics.

### Shared-kernel exemption

A value object in `shared.domain` **cannot** satisfy the first clause: it has no domain
exception available to it. `architecture.definition.md` § 9 forbids `shared` from
depending on any bounded context, so `Money` cannot reference
`masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException`
— and this is not a matter of taste, it is enforced by
`ContextRegistryTest.shared_dependsOnNoBoundedContext`.

The options were:

1. Add a `shared.domain.exception` package with a shared invariant exception. Rejected —
   it grows the shared kernel to serve a handful of scale-and-sign checks, against § 9's
   "keep `shared` minimal", and every context would then map a second exception type.
2. Leave `IllegalArgumentException` and classify it as `BAD_REQUEST` at the boundary.
   **Chosen.**

So `IllegalArgumentException` → `BAD_REQUEST` is mapped in both `*ExceptionResolver`s.
That mapping is a backstop for exactly two things — shared-kernel value objects, and
`UUID.fromString` on a GraphQL `ID` argument — and it is **not** a licence for a
context-owned value object to skip the first clause. Those have a domain exception
available; they must use it.

------------------------------------------------------------------------

## 7. KDoc Standard

### 7.1 Required

-   public types in `core.domain`, `core.inport`, `core.outport`
-   public functions on ports and drivers
-   every nullable property whose absence carries meaning (§ 1.4)

### 7.2 Template — Type

```kotlin
/**
 * One sentence purpose.
 *
 * Details about invariants and semantics.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
```

### 7.3 Template — Function

```kotlin
/**
 * Verb phrase description.
 *
 * @param name meaning and constraints
 * @return meaning
 * @throws SomeDomainException when condition
 */
```

`@return` does not need "never null" — the type says it. That is § 1.4 paying for
itself in documentation as well as in code.

------------------------------------------------------------------------

## 8. Boundary Mutation Rules

-   Mutation MAY happen in the persistence and deserialization layers.
-   MUST rebuild immutable domain instances before propagation.
-   MUST NOT leak partially mutated domain objects.
-   A JPA entity is mutable by necessity (§ 4.6). It MUST NOT escape
    `outbound.persistence`.

------------------------------------------------------------------------

## 9. Code Hygiene

-   Long-living TODOs MUST reference a ticket or ADR.
-   Use interfaces in signatures, concrete types in construction.
-   Use sequences/streams when they clarify intent, not to avoid a loop.
-   `@Suppress` MUST carry a comment saying why the rule does not apply. An
    unexplained suppression is a disabled rule with extra steps.

------------------------------------------------------------------------

## 10. Enforcement

| Concern | Tool |
|---------|------|
| Formatting | Spotless + ktlint (`spotlessCheck`, wired into `check`) |
| Static analysis | detekt, config in `config/detekt/detekt.yml` |
| Compiler strictness | `allWarningsAsErrors`, `-Xjsr305=strict` |
| Dependency and role rules | ArchUnit (`adr/0007-archunit-boundary-enforcement.adr.md`) |
| Judgement calls | `ddd-hex-reviewer` |

The division is deliberate: the first four are deterministic and run on every build;
the last is probabilistic and runs on every increment. Neither subsumes the other.

------------------------------------------------------------------------

End of document.
