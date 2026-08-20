# Coding Style Definition (Java) --- AlpineBooking / SDD Reference

## Purpose

This document defines the coding style for the project to ensure: -
consistent readability and maintainability - predictable navigation
(package / class roles) - consistent use of modern Java features
(records, sealed, pattern matching) - consistent JavaDoc usage suitable
for Spec Driven Development (SDD)

Scope: - Production code (domain, application/use-cases, adapters) -
Tests (naming and structure)

------------------------------------------------------------------------

## 1. General Principles

### 1.1 Explicit Roles over Cleverness

-   Classes MUST communicate intent by name + package.
-   Prefer small, role-specific types over large "god classes".

### 1.2 Immutability First

-   Domain and DTO structures SHOULD be immutable.
-   Mutation is allowed only at boundaries (e.g., UI binding,
    deserialization) and MUST be isolated.

### 1.3 Always-Valid

-   Domain objects MUST NOT be constructible in an invalid state.
-   Validation belongs to constructors/factories/value objects.

### 1.4 Null Policy

-   Domain and application layer MUST NOT return null.
-   Use Optional`<T>`{=html} for absence, not null.
-   At boundaries (e.g., REST), nulls MUST be normalized immediately.

------------------------------------------------------------------------

## 2. Language Level & Modern Java Policy

Target: Java 25 (LTS baseline — `adr/0006-java-25-baseline.adr.md`)

### 2.1 record

Use record when: - the type is a pure data carrier - equality is
structural - it has no lifecycle/state transitions

Records MAY validate invariants in the canonical constructor.

### 2.2 sealed / non-sealed

Use sealed hierarchies when: - the set of subtypes is closed and
meaningful - you want exhaustive switch handling

Rules: - A sealed root type MUST list permitted subtypes explicitly. -
Subtypes SHOULD be final unless extensibility is deliberate. - Prefer
sealed interface for role/contract types.

### 2.3 Pattern matching & switch expressions

-   Prefer switch expressions over cascaded if/else.
-   Use pattern matching for instanceof where it increases readability.

### 2.4 strictfp

-   strictfp MUST NOT be used by default.
-   Only allowed if deterministic floating-point behaviour is a business
    requirement and documented via ADR.

------------------------------------------------------------------------

## 3. Package & Layer Conventions (Hexagonal)

### 3.1 Package naming

-   Base package:
    com.dominikgaller.`<project>`{=html}.`<context>`{=html}
-   Packages MUST be lowercase.
-   No generic util dumping ground.

### 3.2 Layer naming

**Defined in `architecture.definition.md` § 3 and § 4. Not restated here.**

The ontology is `core` (`domain` / `inport` / `outport`) · `inbound`
(`driver` / `listener` / `rest`) · `outbound` (`persistence` / `integration`) ·
`bootstrap` · `shared`.

This section previously described a different layering — `domain / application / in /
out / adapter.*`, with direction `adapter → application → domain`. No code in the
repository has ever followed it, and it contradicted the higher-ranked
`architecture.definition.md`. It was dead text that would mislead anyone reading it as
authoritative, so it is removed rather than reconciled
(`file-usage.definition.md` § 4).

### 3.3 REST split

-   \*RestAPI = the HTTP contract: an interface carrying all Spring MVC annotations
-   \*Controller = the web adapter: implements \*RestAPI, carries no HTTP annotations
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

### 4.2 Methods

-   Commands MUST be verbs
-   **Lookup** queries on a repository or port MUST start with `find*`, `get*` or `load*`
    — e.g. `findById`, `findConfirmedByTourId`
-   **Accessors** are exempt and use the component-reader style: `status()`,
    `bookingId()`, `value()`, `now()`

The exemption is structural, not stylistic: records generate component accessors without a
prefix, and § 2.1 mandates records for data carriers. A `get*` prefix on a record is not
available, so a rule requiring it would forbid the construct the same document requires.

> Previously an unqualified "Queries MUST start with get*, find*, load*", which roughly
> forty domain accessors violated. Found by `ddd-hex-reviewer`, which flagged its own
> uncertainty about whether the rule was ever meant to cover value accessors — it reads as
> though it does, which is the problem.

### 4.3 Variables

-   Prefer final var for locals when type is obvious
-   Prefer explicit types in public APIs

------------------------------------------------------------------------

## 5. Modifiers & Structure

### 5.1 Visibility

-   Default to most restrictive visibility
-   Fields MUST be `private`
-   Fields MUST additionally be `final` **except** aggregate and entity state that a
    state-transition method mutates

The exception is not a concession, it is the point: an aggregate with a lifecycle cannot
have an all-`final` state. `TourBooking.status`, `.participantCount`, `.availableCapacity`
and `GuideTour.status`, `.startedAt` are mutated by `confirm`, `cancel`, `markActive`,
`changeParticipants` and `start`. What the rule protects is preserved by other means:
mutation happens only inside the aggregate, only through named transition methods that
enforce the invariants, and there are no setters (`modelling.definition.md`,
`sdd.playbook.md` § 8) — a rule ArchUnit enforces.

Everything else stays `final`: value objects, records, DTOs, collaborator references in
drivers, adapters and configs.

> Previously an unqualified "Fields MUST be private final", which every aggregate in the
> project violated. Same defect class as § 3.2's dead layering and `test.definition.md`
> § 5.1's unused naming style: a rule written as aspiration and never true.
> Found by `ddd-hex-reviewer`.

### 5.2 final usage

-   Parameters SHOULD be final consistently
-   Local variables SHOULD be final when meaningful

### 5.3 Constructors

-   Prefer constructor injection
-   Lombok allowed in adapters/application
-   Domain SHOULD avoid Lombok unless justified

------------------------------------------------------------------------

## 6. Error Handling

### 6.1 Application Layer

-   MUST NOT return null
-   Use Optional for queries
-   Use explicit exceptions for command failures

### 6.2 Exception taxonomy

-   NotFoundException -\> HTTP 404
-   ValidationException -\> HTTP 400
-   ConflictException -\> HTTP 409

Do NOT use IllegalArgumentException for business semantics.

**The test is where the value comes from, not what validates it upstream.**

- A value object constructed from a **command** — i.e. from data that entered through an
  inport — MUST throw a **domain exception** on an invariant violation. Boundary validation
  is an adapter concern and is **not** part of the core's contract: the same use case may
  later be driven by a message consumer or a scheduler with no Bean Validation in front of
  it, and the value object is then the only guard. The domain may not assume an adapter
  ran.
- A value object constructed from an **internal or infrastructure source**, never from a
  command, MAY throw `IllegalArgumentException` — reaching it means a programmer error.
- `Objects.requireNonNull` for null guards is out of scope; nulls are always programmer
  errors.

Applied to the current value objects:

| Type | Constructed from | Throws | Correct? |
|------|------------------|--------|----------|
| `ParticipantCount` | command (`RequestTourBooking`, `ChangeParticipants`) | `InvalidBookingRequestException` | **Yes** |
| `TourId` | command (`RequestTourBookingDriver`) | `IllegalArgumentException` | **No** — needs a domain exception |
| `ParticipantContact` | command (`RequestTourBookingDriver`) | `IllegalArgumentException` | **No** — needs a domain exception |
| `AvailableCapacity` | `AvailabilityChecker` outport | `IllegalArgumentException` | **Yes** |

> **This rule was wrong on its first attempt, twice over.** `ddd-hex-reviewer` first named
> `ParticipantCount` as the outlier; I replaced that with a "reachability from a client"
> criterion and named `TourId` instead. The reviewer then showed the criterion refuted its
> own conclusion: `tourId`, `contactName`, `contactEmail` and `participantCount` all carry
> Bean Validation on a `@Valid` endpoint, so all four were equally "unreachable" and the
> criterion cleared everything — including the row it was written to condemn. The
> discriminator above (source of the value, not upstream validation) is the one that
> actually decides the four rows, and it makes **two** of them wrong rather than one.

Fixing `TourId` and `ParticipantContact` changes exception types, which is behaviour
change: `IllegalArgumentException` is currently unmapped and surfaces as 500, while a
domain exception maps to 400. It needs its own RED and its own increment — the same
increment as the unmapped `IllegalArgumentException` from `UUID.fromString` recorded in
`ports/start-tour.inport.spec.md` § 7, since both are the same defect.

------------------------------------------------------------------------

## 7. JavaDoc Standard

### 7.1 Required

-   public types in domain, application, in, out
-   public methods on ports and drivers

### 7.2 Template --- Type

/\*\* \* One sentence purpose. * * Details about invariants and
semantics. * * SDD: Reference to spec. \*/

### 7.3 Template --- Method

/\*\* \* Verb phrase description. * * @param name Meaning and
constraints \* @return Meaning (never null) \* @throws Exception when
condition \*/

------------------------------------------------------------------------

## 8. Boundary Mutation Rules

-   Mutation MAY happen in UI binding layer.
-   MUST rebuild immutable domain instances before propagation.
-   MUST NOT leak partially mutated domain objects.

------------------------------------------------------------------------

## 9. Code Hygiene

-   Long-living TODOs MUST reference ticket or ADR.
-   Use interfaces in signatures, concrete types in construction.
-   Use streams when they clarify intent.

------------------------------------------------------------------------

## 10. Enforcement

Recommended: - Spotless (formatter) - Checkstyle or ErrorProne -
ArchUnit for dependency rules

------------------------------------------------------------------------

End of document.
