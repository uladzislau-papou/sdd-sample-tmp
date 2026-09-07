# ADR 0014 – Every Rule Class Has an Executable Owner

## Status
Accepted (inherited from template)

## Context

ADR-0007 introduced ArchUnit because the architecture rules "were previously enforced only
when a human or an agent remembered to check them". That reasoning is not specific to
architecture, and the same failure kept recurring elsewhere in this repository:

- `coding-style.definition.md` § 3.2 described a layering **no code had ever followed**.
- § 1.4 forbade returning null while four shipped, reviewed records relied on an unwritten
  carve-out.
- § 5.1 required fields to be `private final` while **every aggregate violated it**.
- The bounded-context registry lived as prose in one place and as a string literal in a
  test, so adding a context meant editing both and the test would keep passing against the
  stale list.
- The specs' Definition of Done cites tests by name. Porting the example to Kotlin renamed
  nearly every test method, and **66 of 69 citations went stale in one commit** while every
  box stayed ticked and every gate stayed green.

Each was found by review, not by a build. A rule with no executable owner does not survive
contact with a refactor.

## Decision

Every class of rule names its enforcer, and the list is written into
`coding-style.definition.md` § 9:

| Rules | Enforced by |
|-------|-------------|
| formatting, imports, line length | ktlint via spotless, configured in `.editorconfig` |
| complexity, swallowed exceptions, nullability leaks | detekt, `config/detekt/detekt.yml` |
| layering, dependency direction, class roles | ArchUnit — `DependencyRulesTest`, `ClassRoleRulesTest` |
| the bounded-context registry | `ContextRegistryTest`, **parsing `architecture.definition.md` § 11** |
| test citations in specs | `SpecCitationsTest`, scanning `documentation/` |
| compiler-visible defects | `allWarningsAsErrors = true`, `-Xjsr305=strict` |
| everything else | `ddd-hex-reviewer` and human review |

Two obligations follow:

1. **A document that a test parses carries a written format contract** beside the content,
   stating the shape the parser depends on. § 11 has one.
2. **When a rule in the last row rots, the fix is to move it up a row**, not to restate it
   more firmly.

## Rationale

**Why parse the document rather than duplicate it in the test.** `CLAUDE.md` already names
two lists that must exist in exactly one place. The context registry was a third, and it was
copied. Parsing makes the document the source and makes disagreement a build failure
instead of a discrepancy nobody looks for.

**Why a test and not a script.** The previous plan carried "every citation resolves to a
test that exists (scripted)" as a checklist item. A script runs when somebody remembers.

**Why suppressions must carry reasons.** Two gate findings on the first Kotlin slice were
genuine and needed departures: detekt's `SpreadOperator` fires on Spring Boot's idiomatic
Kotlin entry point, and `VarCouldBeVal` fires on the `lateinit var` that `@MockitoBean`
requires. Both were recorded with their reasons — in `config/detekt/detekt.yml` and, for the
second, in `coding-style.definition.md` § 5.2 as well, because a rule and its enforcement
disagreeing is worse than either being wrong. A suppression without a reason is
indistinguishable from a rule nobody understood.

**Why gates are also allowed to be honest about what they cannot check.** § 9's last row is
an admission, and the review gate in `test.definition.md` § 7 splits authority by axis for
the same reason: architecture and spec conformance block because they rest on executable
rules and list-matching, while logic and security only report, because a blocking gate that
cries wolf gets switched off entirely — taking the reliable axes with it.

## Consequences

- Renaming a package cannot silently disarm the architecture tests: `ArchitectureRoot`
  derives the package root from where `ServiceApplication` actually is, instead of three
  tests repeating a literal.
- Restyling § 11's table breaks the build. That is the intended trade: the format contract
  is written beside it.
- New gates cost build time. `SpecCitationsTest` walks `documentation/` on every run; it
  found four genuine coverage gaps on its first execution.
