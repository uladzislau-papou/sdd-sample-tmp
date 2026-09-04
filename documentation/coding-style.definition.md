# Coding Style Definition (Kotlin) — Risk Management Service

## Purpose

Consistent readability, predictable navigation, idiomatic Kotlin, and KDoc that carries
enough intent to support Spec Driven Development.

Scope: production code (`src/main/kotlin`) and tests (`src/test/kotlin`).

**Formatting is not this document's job.** Spotless with ktlint 1.5.0 owns formatting, and
detekt owns the smell rules. Both are merge blockers (`test.definition.md` § 7). This
document covers what a formatter cannot check: naming, structure, roles, documentation
and error semantics.

---

## 1. General Principles

### 1.1 Explicit roles over cleverness

- A class's name plus its package MUST tell you why it exists.
- Prefer several role-specific classes over one that does everything. `kyc/service/` is
  sub-packaged by concern (`screening`, `research`, `coredata`, `document`, …) for exactly
  this reason.

### 1.2 Immutability where it is free

- `val` by default. `var` only where the value genuinely changes.
- On entities, `var` is expected for lifecycle columns — see `modelling.definition.md`
  § 2.2.
- DTOs, input types, provider models, configuration properties and mapper outputs are
  all-`val` `data class`es.

### 1.3 Null policy

- Use Kotlin's nullable types (`T?`). **Never `Optional<T>`.**
- Nullable is for **real absence**, not "not filled in yet". If a value must eventually
  exist, model the state that says so.
- **Every nullable property and parameter MUST document what `null` means**, via a
  `@property` / `@param` KDoc tag. An undocumented nullable is a violation of this
  section. `KycCase` documents all of them; use it as the reference.
- Nulls arriving from outside Kotlin's type system — JSON deserialization, provider
  responses, JPA — are normalized at the boundary that receives them.
- `!!` is forbidden outside tests. If you know it is non-null, prove it with a check that
  throws a typed exception (`modelling.definition.md` § 4.1).

### 1.4 Compiler strictness

`build.gradle.kts` sets `allWarningsAsErrors = true` and `-Xjsr305=strict`.

- A warning is a build failure. Do not suppress with `@Suppress` to get to green; fix it,
  or state why the suppression is correct in a comment on the same line.
- `@Suppress` on a whole class or file is drift.

---

## 2. Language Level

Kotlin **2.3.0**, JVM target **17**, Spring Boot **4.0.3**
(`adr/0001-technical-stack.adr.md`). Raising any of these is an ADR trigger
(`sdd.playbook.md` § 6 item 13).

### 2.1 `data class`

Use for every pure data carrier: entities, DTOs, input types, provider models,
configuration properties.

Do not hand-write `equals`/`hashCode`/`toString`/`copy`.

### 2.2 Sealed hierarchies

Use `sealed interface` / `sealed class` where the set of subtypes is closed and you want
exhaustive `when`. Prefer `sealed interface` for role contracts.

### 2.3 `when`

- Prefer `when` over cascading `if`/`else`.
- **A `when` over an enum or sealed type MUST be exhaustive without `else`.** An `else`
  branch over a provider status enum is how a new provider status silently becomes
  "unknown" — the compiler is the only thing that will tell you the provider added one.
- An `else` is acceptable only where the input genuinely is open (a raw `String` from a
  provider), and then it MUST throw or record, never silently default.

### 2.4 Nullability at the JPA boundary

Kotlin's `plugin.jpa` synthesises no-arg constructors; Hibernate populates by reflection.
A property typed `String` (non-null) whose column is nullable will hold `null` at runtime
with no error. **The Kotlin type MUST match the column's nullability.** A mismatch is
drift.

---

## 3. Package & Layer Conventions

### 3.1 Package naming

- Base: `com.jobradleasing.riskmanagementservice.<module>`
- Packages are lowercase, no underscores, no camelCase.
- No generic `util` dumping ground. A `util` package exists per module and is for genuine
  cross-cutting helpers; business logic there is drift.
- Do not add packages under `qes/validation/annatation/` — it is a registered
  misspelling (`architecture.definition.md` § 11.3).

### 3.2 Layer naming

**Defined in `architecture.definition.md` § 3 and § 4. Not restated here.**

`api` (`controller` / `dto` / `input` / `mapper` / `integration`) · `service` ·
`repository` · `domain` (`model` / `enums` / `exception`) · `config` · `validation` ·
`web` (`advice` / `auth`).

---

## 4. Naming Conventions

### 4.1 Types

| Suffix | Role | Package |
|--------|------|---------|
| `*Controller` | GraphQL or REST delivery | `api/controller` |
| `*Dto` | Outbound payload | `api/dto` |
| `*Input` | Inbound GraphQL argument type | `api/input` |
| `*Request` / `*Response` | Inbound/outbound REST wire type | `api/model` or `api/input`/`api/dto` |
| `*Mapper` / `*Assembler` | Pure translation | `api/mapper` |
| `*Client` | Outbound HTTP client | `api/integration/<provider>/client` |
| `*Service` | Business logic, transaction owner | `service` |
| `*QueryService` | Read-only service | `service` |
| `*Scheduler` | `@Scheduled` trigger, delegates to a service | `service` |
| `*Repository` | Spring Data JPA interface | `repository` |
| `*Properties` | `@ConfigurationProperties` | `config/property` |
| `*Exception` | Typed failure | `domain/exception` |
| `*Strategy` | Per-provider behaviour behind a common interface | `service/<concern>/strategy` |

A class whose name does not match its role's suffix is drift, and so is one whose suffix
does not match its package.

### 4.2 Functions

- Commands are verbs: `acceptCase`, `declineCase`, `collectParties`, `startSignature`.
- Lookups start with `find*` (nullable), `get*` (throws), or `load*`.
  `findByCaseIdOrThrow` names both halves and is the preferred shape when a caller has no
  meaningful "absent" branch.
- Boolean-returning functions read as predicates: `isTerminal`, `hasPendingDocuments`,
  `canTransitionTo`.
- Kotlin property accessors take no prefix. A `val status` is `status`, never `getStatus()`.

### 4.3 Test names

`<methodOrSubject>_<condition>_<expectedResult>`, matching the dominant convention in
`src/test/kotlin`:

```
acceptCase_throwsBadUserInput_whenCaseIsDeclined
collectParties_advancesToPartiesCollected_whenFunctionaryAndUboExist
mapDecision_mapsAllRadarStatuses_exhaustively
```

The subject comes first so tests for one behaviour sort together and a failure name points
at the production method.

---

## 5. Structure

### 5.1 Visibility

- Default to the most restrictive that compiles: `private` → `internal` → public.
- A public function on a service that nothing outside the module calls should be
  `internal`.
- Spring beans (`@Service`, `@Controller`, `@Component`) and JPA entities must remain
  public/open enough for proxying — `kotlin("plugin.spring")` and `kotlin("plugin.jpa")`
  handle the `open` part; do not add `open` by hand.

### 5.2 Constructors

- **Constructor injection only.** No `@Autowired` on fields, no `lateinit var`
  collaborators.
- Primary-constructor properties: `class KycCaseDecisionService(private val repo: …)`.
- A constructor with more than ~6 collaborators is a signal the class does too much.
  Split by concern before adding a seventh.

### 5.3 Function size

detekt enforces the numeric limits. Beyond those: a service method that reads as
*load → decide → mutate → persist → emit* is the target shape. When a step needs more than
a few lines, extract it as a private function named after the step.

### 5.4 Line length

120 characters (`.editorconfig`, ktlint).

---

## 6. Error Handling

**Taxonomy and semantics: `modelling.definition.md` § 4. Not restated here.**

Style rules that belong to this document:

- Throw typed exceptions from `domain/exception`. Never `IllegalArgumentException`,
  `IllegalStateException` or bare `RuntimeException` for business semantics.
- An exception message names the entity, the attempted action and the blocking state:

  ```kotlin
  throw BadUserInputException(
      "Cannot apply ${KycCaseEvent.COLLECT_PARTIES} to KYC case '${kycCase.id}' in status ${kycCase.status}.",
  )
  ```

- Never interpolate personal data, tokens, or provider payloads into a message.
- `runCatching` is acceptable only where every branch of the result is handled. Swallowing
  a `Throwable` into a default value is forbidden.
- `try`/`catch (e: Exception)` at a service boundary must re-throw a typed exception and
  attach the cause.

---

## 7. Logging

`io.github.oshai:kotlin-logging-jvm`.

```kotlin
private val logger = KotlinLogging.logger {}
```

- Lazy message lambdas: `logger.info { "…" }`, never string concatenation.
- **No personal data, no tokens, no full provider payloads.** Log identifiers, statuses
  and correlation ids.
- `INFO` for lifecycle milestones, `WARN` for a recoverable external failure, `ERROR` for
  something a human must look at. A retried delivery attempt is `WARN`, not `ERROR`.
- A caught-and-rethrown exception is logged once, at the place that decides what to do
  about it — not at every level on the way up.

---

## 8. KDoc Standard

### 8.1 Required

- Every public class in `domain/model`, `service`, `api/controller`,
  `api/integration/*/client` and `repository`
- Every public function on a service or controller
- **Every nullable property and parameter** (§ 1.3)
- Every repository query whose selection criterion is not obvious from its name

### 8.2 Type template

```kotlin
/**
 * One sentence stating what this is.
 *
 * Details: lifecycle, invariants, who owns writes to it.
 *
 * @property foo what it holds; for nullable, what absence means
 */
```

### 8.3 Function template

```kotlin
/**
 * Verb phrase: what it does.
 *
 * Legal from <state>. Attributed to <actor>. <transaction/idempotency note>.
 *
 * @param input what it carries and what constrains it
 * @return what comes back
 * @throws BadUserInputException when <condition>
 */
```

`KycCaseDecisionController` and `KycCase` are the reference implementations of this
standard — match their density.

---

## 9. Hygiene

- A `TODO` MUST reference a ticket or an ADR. An unattributed `TODO` is drift.
- Use interfaces in signatures, concrete types at construction.
- Prefer Kotlin's collection operators where they clarify intent; reach for `Sequence`
  only when the pipeline is large enough for intermediate allocation to matter.
- Dead code is deleted, not commented out. Git remembers.
- Do not add a dependency without an ADR (`sdd.playbook.md` § 6 item 2).

---

## 10. Enforcement

| Tool | Owns | Config |
|------|------|--------|
| Spotless + ktlint 1.5.0 | Formatting, import order, line length | `build.gradle.kts` |
| detekt 2.0.0-alpha.2 | Complexity, smells, empty blocks, unused parameters | `config/detekt/detekt.yml` |
| Kotlin compiler | `allWarningsAsErrors`, `-Xjsr305=strict` | `build.gradle.kts` |
| lefthook | Runs the above pre-commit; tests pre-push | `lefthook.yaml` |
| This document + review | Roles, naming, KDoc, error semantics | — |

Run `./gradlew spotlessApply` **before** `detekt` — the reverse order produces false
positives (`README.md` § Pre-commit Checklist).
