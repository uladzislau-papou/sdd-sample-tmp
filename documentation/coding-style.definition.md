# Coding Style Definition (Kotlin)

## Purpose

This document defines the coding style for a service built from this template, so that:

- readability and navigation are predictable — a class's role is legible from its name
  and its package, before opening it;
- Kotlin's own guarantees are used instead of re-implemented — nullability, immutability
  and exhaustiveness are expressed in types, not in prose;
- KDoc carries the link back to the spec that justifies the code (SDD).

Scope: production code (domain, use cases, adapters) and tests (naming and structure).

**This is a profile document.** It ranks third in `CLAUDE.md`'s authority order and it
describes *this* project. A service adopting the template may take it as it stands — very
little of it is domain-specific — but it owns the file and may replace it. Where a rule
below names a Master or a Contract, that is the example talking, not a requirement.

**This document replaced a Java one.** The rules it inherited were kept whenever Kotlin
still needs them and dropped whenever the language already enforces them. Each drop is
recorded, because a rule that vanishes without explanation reads as an oversight.

---

## 1. General Principles

### 1.1 Explicit roles over cleverness

- A class MUST communicate intent by name plus package.
- Prefer small, role-specific types over large classes that accumulate responsibilities.

### 1.2 Immutability first

- Domain values and DTOs MUST be immutable: `val` properties, `data class`.
- Mutation is allowed only where a lifecycle genuinely exists — aggregate state — and is
  confined to the aggregate (see § 5.1).

### 1.3 Always-valid

- A domain object MUST NOT be constructible in an invalid state.
- Validation belongs in `init` blocks, factory functions and value objects — never in a
  separate validator that a caller may forget to run.

### 1.4 Null policy

- Absence is expressed by a **nullable type** (`T?`). Nothing else.
- The domain and the use-case layer MUST NOT use a platform type — every declaration is
  either `T` or `T?`, deliberately.
- At an adapter boundary, incoming nulls MUST be normalized immediately, so that nothing
  nullable travels further inward than the adapter unless the domain says absence is
  meaningful.
- A nullable property MUST document **what absence means**. `reason: String?` is
  incomplete; "null when the guide gave no reason" is the rule satisfied.

> **Two paragraphs of the Java original are deliberately gone.** It required `Optional<T>`
> for absence and then needed a long, three-clause exception permitting nullable record
> components, because `Optional` fields are bad practice in Java while nullable fields are
> unrepresentable in its type system. Kotlin has one mechanism for both cases, so the rule
> and its exception collapse into the four lines above. `Optional<T>` MUST NOT be used in
> Kotlin code; it is a Java-interop type, and using it here would reintroduce exactly the
> asymmetry the original had to apologise for.
>
> What survives from the original is the third clause, and it is the one that mattered:
> absence must be a **real business case**, and it must be documented. "Not filled in yet"
> is not a business case — that is a missing invariant wearing a null.

---

## 2. Language level

Target: **Kotlin 2.3 on JVM 17** (`adr/0010-jvm-17-baseline.adr.md`).

Compiler settings are gates, not preferences (`build.gradle.kts`):

| Setting | Why |
|---|---|
| `allWarningsAsErrors = true` | A warning nobody must fix is a warning nobody reads |
| `-Xjsr305=strict` | Java-interop annotations become real nullability, not platform types — § 1.4 is otherwise unenforceable across a boundary |

### 2.1 `data class`

Use `data class` when the type is a data carrier, equality is structural, and there is no
lifecycle. Validate invariants in `init`.

Do **not** use `data class` for an aggregate. Structural equality is wrong for an entity —
two Contracts with identical fields are not the same Contract — and a generated `copy()`
hands every caller a way around the aggregate's transition methods.

### 2.2 `value class`

Permitted for a single-field wrapper with no validation. A value object that validates
SHOULD be a plain `data class`: the allocation saved is not worth the constraints
inline classes place on `init` and on interop.

### 2.3 `sealed`

Use a sealed hierarchy when the set of subtypes is closed and meaningful, and exhaustive
`when` handling is wanted. Prefer `sealed interface` for role and contract types.

An exhaustive `when` over a sealed type or an enum MUST NOT carry an `else` branch:
`else` converts "the compiler will tell you when a case is added" into "the new case is
silently swallowed", which is the whole reason for sealing the type.

### 2.4 Expression bodies

Prefer an expression body when the function is a single expression. Declare the return
type explicitly on anything public.

### 2.5 Scope functions

`let`, `also`, `apply`, `run` and `with` are permitted where they shorten a genuine
sequence on one receiver. They MUST NOT be nested: two levels of implicit `it` is a
readability defect, not a style preference.

---

## 3. Package and layer conventions (hexagonal)

### 3.1 Package naming

- Base package: `com.<org>.<service>.<context>`
- Packages MUST be lowercase and MUST NOT be a generic dumping ground (`util`, `common`,
  `helpers`).

### 3.2 Layer naming

**Defined in `architecture.definition.md` § 3 and § 4. Not restated here.**

The ontology is `core` (`domain` / `inport` / `outport`) · `inbound` (`driver` /
`listener` / `rest` / `graphql`) · `outbound` (`persistence` / `integration`) ·
`bootstrap` · `shared`.

> The Java original described a *different* layering here — `domain / application / in /
> out / adapter.*` — that no code had ever followed and that contradicted the
> higher-ranked `architecture.definition.md`. It was removed rather than reconciled
> (`file-usage.definition.md` § 4). The lesson generalizes: this section states where the
> ontology lives instead of copying it.

### 3.3 The delivery split

One inbound port, one or more delivery adapters. Each transport contributes a pair:

| Role | What it is |
|---|---|
| `*RestAPI` | the HTTP contract — an interface carrying **all** Spring MVC annotations |
| `*RestController` | the REST adapter — implements `*RestAPI`, carries **no** HTTP annotations |
| `*GraphQLAPI` | the GraphQL contract — an interface carrying all `@QueryMapping` / `@MutationMapping` annotations |
| `*GraphQLController` | the GraphQL adapter — implements `*GraphQLAPI`, carries no GraphQL annotations |

Rules that apply to every delivery adapter:

- It MUST only map, normalize, delegate and translate errors.
- It MUST depend on `core.inport` interfaces, never on a driver.
- It MUST NOT contain a business rule. If a check cannot be expressed as input validation,
  it belongs in the domain.

**Why `*RestController` and not `*Controller`.** Spring for GraphQL annotates its
resolvers with `@Controller`. With the Java original's naming, a GraphQL resolver and a
REST adapter would both have claimed the `*Controller` suffix, while `ClassRoleRulesTest`
decides a class's role **by its name**. The gate would have had to either ignore GraphQL
resolvers or misclassify them. Naming the transport explicitly keeps the rule decidable.

**Terminology.** `*RestAPI` and `*GraphQLAPI` are **not** inbound ports. The inbound port
is `core.inport.usecase.*UseCase`. These are delivery-side interfaces that exist so each
transport's contract sits in one readable place — and so that the same use case reached
over two transports demonstrably shares one core. See `architecture.definition.md` § 4.5.

---

## 4. Naming conventions

### 4.1 Types

| Suffix | Role |
|---|---|
| `*UseCase` | inbound port — the interface the core exposes |
| `*Driver` | application service — the inbound port's implementation |
| `*Command` / `*Result` | the inbound port's input and output |
| `*RestAPI` / `*RestController` | REST contract and adapter (§ 3.3) |
| `*GraphQLAPI` / `*GraphQLController` | GraphQL contract and adapter (§ 3.3) |
| `*Repository` | outbound port |
| `*JpaEntity` | persistence-layer row type — lives in `outbound.persistence`, never in `core` |
| `*Mapper` | pure mapping component, no IO |
| `*Listener` | inbound adapter driven by an event rather than a request |

### 4.2 Functions

- A command MUST be a verb: `confirm`, `markActive`, `start`.
- A **lookup** on a repository or port MUST start with `find`, `get` or `load` —
  `findById`, `findActiveByCustomerNumber`.
- A **property read** is exempt and needs no prefix. In Kotlin it is usually not a function
  at all: `val status`, `val masterId`.

> The Java original had to spell this exemption out at length, because records generate
> unprefixed accessors while the rule demanded a `get` prefix — a rule forbidding the
> construct the same document required. In Kotlin a property is a property, so the
> exemption is a single line. Found originally by `ddd-hex-reviewer`, which flagged that
> roughly forty domain accessors violated the unqualified version of the rule.

### 4.3 Domain vocabulary is English

Every identifier is English, including domain terms whose sources are not. A source-language
term is not carried into the code, not as a name and not as an alias.

The current domain is invented and English throughout (`project.definition.md`), so this rule
has **nothing to translate today**. It is kept rather than deleted because the reasoning is
what matters and it is cheap to lose:

An earlier version of this service was specified from German sources, and this section
carried a translation table — *Leasingrahmenvertrag* to `MasterLeasingContract`,
*geldwerter Vorteil* to `monetaryBenefit`. What that table recorded, and what is worth keeping,
is the **cost**: some terms are terms of law, and the English is a translation rather than a
synonym. `monetaryBenefit` reads as "a benefit worth money" while *geldwerter Vorteil* is the
taxable value of private use. The translation is load-bearing, and a disputed one is settled by
going back to the source document — never by re-reading the code or a table like that one.

The alternative — foreign identifiers where no faithful English exists — was considered and
rejected: it needs a per-term judgement with no executable owner, which is the shape of a rule
that becomes "however the last author felt" within a month.

A context package is named with a short abbreviation and full English words in the type names:
`contract`, holding `Master` and `Contract`.

### 4.4 Declarations

- Prefer inferred types for locals when the type is obvious from the right-hand side.
- Declare explicit types on anything public.

---

## 5. Structure and visibility

### 5.1 Visibility and mutability

- Default to the most restrictive visibility. `internal` is preferred over `public` for
  anything not part of a layer's contract.
- Properties are `val` **except** aggregate and entity state that a transition method
  mutates.
- A mutable aggregate property MUST have a **private setter**:
  `var status: MasterStatus = …; private set`.
- There MUST be no setters on an aggregate beyond that, and no `copy()` — see § 2.1.

The mutability exception is not a concession, it is the point: an aggregate with a
lifecycle cannot have all-immutable state. What the rule protects is preserved by other
means — mutation happens only inside the aggregate, only through named transition methods
that enforce the invariants, and `private set` makes that structural rather than
conventional. ArchUnit enforces the absence of setters
(`modelling.definition.md`, `sdd.playbook.md` § 8).

> The Java original said "fields MUST be private final", which every aggregate in the
> project violated. Same defect class as § 3.2's dead layering: a rule written as
> aspiration and never true. Found by `ddd-hex-reviewer`. Kotlin's `private set` is what
> makes the corrected rule enforceable instead of merely stated.

### 5.2 Constructors and injection

- Constructor injection only. No field injection, no `lateinit` for collaborators.

  **Scope: production code.** A Spring test declares injected collaborators as
  `lateinit var`, because that is the only form `@MockitoBean` and `@Autowired` field
  overrides accept — `val` does not compile there. Detekt's `VarCouldBeVal` is therefore
  switched off for test sources and left on for production
  (`config/detekt/detekt.yml`). The exception is named in both places rather than in one,
  because a rule and its enforcement disagreeing is worse than either being wrong.
- A class with a single constructor declares it in the class header.
- No annotation processors and no code-generation plugins for boilerplate: Kotlin's
  primary constructors and `data class` remove the need, and generated code is code that
  no reviewer reads.

---

## 6. Error handling

### 6.1 Use-case layer

- MUST NOT return a sentinel to signal failure — no `null` for "it went wrong", no
  boolean flag.
- Absence in a *query* is a nullable return (§ 1.4).
- Failure of a *command* is an explicit exception.

### 6.2 Exception taxonomy

| Kind | HTTP |
|---|---|
| not-found | 404 |
| validation | 400 |
| conflict | 409 |

`IllegalArgumentException` MUST NOT carry business semantics. **The test is where the
value comes from, not what validates it upstream:**

- A value object constructed from a **command** — data that entered through an inport —
  MUST throw a **domain exception** when an invariant is violated. Boundary validation is
  an adapter concern and is not part of the core's contract: the same use case may later be
  driven by a message consumer or a scheduler with no Bean Validation in front of it, and
  the value object is then the only guard. **The domain may not assume an adapter ran.**
- A value object constructed only from an **internal or infrastructure source** MAY throw
  `IllegalArgumentException`. Reaching it means a programmer error.
- Null guards are out of scope — in Kotlin a non-nullable type is the guard.

Clause one triggers on **any** command construction site, so a type with mixed sites falls
under it.

### 6.3 Shared-kernel exemption

A value object in `shared.domain` **cannot** satisfy clause one: it has no domain exception
available. `architecture.definition.md` § 9 forbids `shared` from depending on any bounded
context, so a shared identity cannot reference a context's exception — and this is enforced,
not merely agreed: attempting it fails `ContextRegistryTest`.

The options were to grow `shared` with its own exception package for one blank-string
check, or to let the shared value object throw `IllegalArgumentException` and map it to 400
at every boundary. The second was chosen, and it is more comfortable in Kotlin than it was
in Java: `require(…)` throws exactly that, so the shared kernel's guard is idiomatic rather
than a wart.

`IllegalArgumentException → 400` is therefore mapped in every `*ExceptionHandler`. That
mapping is a backstop for two things — shared-kernel value objects and identifier parsing
on a path variable — and it is **not** a licence for a context-owned value object to skip
clause one. Those have a domain exception available; they must use it.

> **This rule was wrong on its first two attempts**, and the record is kept because the
> failure mode recurs. `ddd-hex-reviewer` first named the wrong outlier; the replacement
> criterion — "reachability from a client" — then refuted its own conclusion, because every
> field on a `@Valid` endpoint is equally unreachable, so the criterion cleared everything
> including the case it was written to condemn. The discriminator that actually decides is
> the **source of the value**, not the validation in front of it.

---

## 7. KDoc standard

### 7.1 Required

- Public types in `core.domain`, `core.inport`, `core.outport`.
- Public functions on ports and drivers.
- Every nullable property, stating what absence means (§ 1.4).

### 7.2 Type template

```kotlin
/**
 * One sentence of purpose.
 *
 * Details about invariants and semantics.
 *
 * SDD: see `documentation/<path-to-spec>.md`.
 */
```

### 7.3 Function template

```kotlin
/**
 * Verb phrase describing the effect.
 *
 * @param name meaning and constraints
 * @return meaning
 * @throws SomeException when the condition holds
 */
```

The `SDD:` line is not decoration. It is the only mechanical link from code back to the
spec that justifies it, and `conformance-reviewer` follows it.

---

## 8. Code hygiene

- A long-living `TODO` MUST reference a ticket or an ADR. An unattributed `TODO` is
  deleted, not kept.
- Use interfaces in signatures and concrete types at construction sites.
- Use sequences and collection operations where they clarify intent; a `for` loop that
  reads better stays a `for` loop.
- No wildcard imports.
- `!!` MUST NOT appear in `core`. In an adapter it requires a comment saying why the value
  cannot be null — and that comment is usually the discovery that the type is wrong.

---

## 9. Enforcement

Prose that nothing checks decays. Each rule class has an owner:

| Rules | Enforced by |
|---|---|
| formatting, imports, line length | ktlint via spotless (`.editorconfig`) |
| complexity, swallowed exceptions, nullability leaks | detekt (`config/detekt/detekt.yml`) |
| layering, dependency direction, class roles (§ 3.3, § 4.1) | ArchUnit — `DependencyRulesTest`, `ClassRoleRulesTest`, `ContextRegistryTest`. **Five exceptions**, listed below |
| everything else in this document, including § 4.3 and the anti-corruption obligation below | `ddd-hex-reviewer`, and human review |

The last row is an admission, not a boast: a rule in that row is a rule that can rot
unnoticed. When one does, the fix is to move it up a row, not to restate it more firmly.

Seven rules sit in that row deliberately, with their weakness named. The first five are a
recent and reversible addition; the last two are permanent.

**Five transport rules lost their enforcer to `adr/0027`**, which deleted the ArchUnit rules
that checked them because `adr/0020` leaves the service with no `inbound.rest` package and no
use case in `uc01`–`uc06` needs an `inbound.listener`. ArchUnit fails a rule that matches
nothing, and an allowance whose precondition can never come true is a permanent, invisible
relaxation — so deletion was chosen over relaxation. The **rules** below still bind; only
their enforcement is gone:

| Rule | Where it is stated | Deleted enforcer |
|---|---|---|
| A `*RestController` carries no HTTP annotation | § 3.3 | `ClassRoleRulesTest` · `restAdapterCarriesNoHttpAnnotation` |
| A `*RestController` is annotated `@RestController` | § 3.3 | the REST half of `ClassRoleRulesTest` · `deliveryAdaptersAreRegisteredWithTheirFramework` |
| No domain type appears in a REST DTO | `architecture.definition.md` § 4.5 | `ClassRoleRulesTest` · `deliveryDtosCarryNoDomainType` |
| A listener does not touch `outbound.*` | `architecture.definition.md` § 4.8 | `ClassRoleRulesTest` · `listenersDoNotTouchOutboundAdapters` |
| `inbound.rest` depends only on `core.inport`, its DTOs and domain exceptions | `architecture.definition.md` § 6 rule 3 | `DependencyRulesTest` · `rule3_restDependsOnInportOnly` |

The deleted enforcers are written `ClassName` · `methodName` rather than
`` `ClassName.methodName` ``, because `SpecCitationsTest` checks that every citation of the
second form names a test that exists — and these deliberately do not. UC01 § 10 uses the same
device for the inverse case, a test not yet written. Both are the same hole in the same gate:
it cannot distinguish a citation that is stale from one whose subject is intentionally absent.
Named here rather than worked around silently.

This is the one case where "move it up a row" is scheduled rather than owed:
`DeletedTransportRulesTripwireTest` fails on the commit that creates either package, and its
message is the instruction to restore all five. So these rules leave the last row on the day
they acquire a subject, and until then nothing can violate them because the packages do not
exist.

The two permanent members:

- **§ 4.3, English vocabulary.** A German identifier is a plain-text pattern and could be
  grepped for. What cannot be checked is the *quality* of a translation, which is where the
  risk actually is — so an executable owner would produce confidence out of proportion to
  what it verifies.
- **The anti-corruption obligation.** No foreign system's representation reaches `core` or
  `shared.domain`. `DependencyRulesTest` already blocks framework and persistence types there,
  and it would block a generated client type by package. What it cannot see is a hand-written
  class that mirrors a foreign payload field-for-field under a domestic name. That is a review
  judgement, and it is written here so that its absence from the gates is a known absence.

  The service currently has **no outbound integration at all**
  (`project.definition.md`, Non-Goals), so this rule has no subject either. It is the first
  thing to re-read when one arrives; `adr/0017-contract-data-ownership-boundary.adr.md` is
  withdrawn but carries the argument in full.

---

End of document.
