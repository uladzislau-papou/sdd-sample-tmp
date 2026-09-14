---
name: code-review
description: Review the diff since a base ref (default: merge-base with main) for logic errors, architecture drift against this project's DDD/Hexagonal rules, and security issues. Runs inline, independent of the ddd-hex-reviewer agent, and reports findings via ReportFindings. Use when asked to review code, review a branch/PR, or check work for bugs before merging.
argument-hint: [<base-ref>] [--paths=<glob>[,<glob>...]]
---

# Code Review

A standalone, on-demand review across three axes: **logic errors**, **architecture
drift**, and **security issues**. It complements — but does not replace — the
`ddd-hex-reviewer` agent that already runs automatically after every GREEN step
in the TDD loop (`execution.playbook.md` § 3.5). That agent does one exhaustive
seven-group architectural sweep; this skill is broader in kind (it also covers
correctness and security) but does not re-run that full sweep. Do not dispatch
`ddd-hex-reviewer` from inside this skill and do not duplicate its seven-group
checklist wholesale — stay complementary.

## Arguments

| Argument | Default | Meaning |
|----------|---------|---------|
| `<base-ref>` | merge-base of `HEAD` and `main` | Compare against this ref instead |
| `--paths=<glob>[,<glob>...]` | off | Review these files/paths in full instead of a diff |

---

## Step 1 — Resolve scope

- If `--paths` is given: the review set is those files, read in full. Say so
  explicitly — this is a whole-file review, not a diff review, and findings may
  include pre-existing issues the diff mode would not surface.
- Otherwise:
  1. `git status` — refuse to attribute findings to this review if there are
     unrelated staged/unstaged changes; say so before continuing.
  2. Resolve the base: the given `<base-ref>`, or `git merge-base main HEAD` if
     omitted. If `HEAD` **is** `main` (nothing to diverge from), say so and stop —
     there is no increment to review.
  3. `git diff <base>...HEAD` for the tracked changes.
  4. `git status --porcelain` to find **untracked new files** the diff above will
     not show, and read those in full — a new file is still part of the increment.

Report the resolved scope (base SHA, file count) before reviewing.

---

## Step 2 — Ground in project rules

Read before judging anything as drift:

- `documentation/architecture.definition.md` — § 3 package structure, § 4
  responsibilities, § 6 dependency rules, § 9 shared kernel, § 10 anti-patterns,
  § 11 registered bounded contexts
- `documentation/coding-style.definition.md` — § 1.4 null policy, § 4 naming,
  § 6.2 exception taxonomy
- `documentation/modelling.definition.md` — Always-Valid doctrine, identity
  categories, Application Service's authorization-boundary responsibility

A finding with no doctrine to cite is either a logic/security finding (cite the
concrete failure instead) or not a finding — do not invent architectural rules.

---

## Step 3 — Review the three axes

Work through all three for every changed file. Do not stop at the first finding
in an axis.

### Logic errors

- Off-by-one, inverted conditionals, wrong boundary (`<` vs `<=`) on price bands,
  dates, or quotas.
- Null/empty handling: a collection or nullable field used without the check the
  surrounding code implies is needed.
- Money and date arithmetic: wrong rounding, wrong unit (cents vs whole units),
  timezone-naive date math.
- Exception type doesn't match what the catch site (or GraphQL classifier)
  expects, so an error is misrouted or swallowed.
- A domain event raised but never drained (`pullDomainEvents()`), or drained but
  the mutation that should have raised it doesn't.
- State mutated on a path that should have been read-only, or vice versa.
- A new branch (new `when`/`if` arm) added with no test exercising it.

### Architecture drift

Condensed from the rules in Step 2 — the highest-value checks, not the full
seven-group sweep:

- A changed top-level package not in § 11's registry.
- `core` or `shared.domain` importing a framework type (Spring, JPA, GraphQL
  annotations), or a JPA entity/mapped reference reaching into domain code.
- A `*Driver` doing status comparisons, price-band comparisons, or date/money
  arithmetic that belongs in the aggregate (anemic domain).
- `Optional` or `!!` inside `core`/`shared`.
- `require(...)` (throws `IllegalArgumentException`) used where a domain
  exception is required by § 6.2.
- A domain type serialised directly in the GraphQL schema instead of mapped to a
  scalar/input type.
- An identity owned by this context carried as a bare primitive instead of a
  value object (or the reverse mistake — see `modelling.definition.md`'s four
  identity categories).

### Security

No dedicated security doc exists yet for this project, so apply standard
secure-coding practice to this stack (Kotlin, Spring Boot, JPA, GraphQL-only
inbound adapter, salary-sacrifice leasing data):

- **Injection**: string-concatenated JPQL/SQL/native queries instead of named
  parameters; any `@Query` built from raw input.
- **Authorization boundary**: a mutation or query resolver that skips the
  authorization check `modelling.definition.md` requires of the Application
  Service layer — check the driver actually applies it, not just that a
  security annotation exists.
- **GraphQL-specific abuse**: unbounded list fields with no pagination, queries
  that allow unbounded nesting/recursion (DoS via query depth), introspection
  left enabled outside dev profiles.
- **Sensitive data exposure**: salary, IBAN/bank-account, or other PII fields
  logged in plaintext, returned in a GraphQL error message, or included in an
  exception's `toString()`.
- **Error leakage**: an exception classifier that lets an internal stack trace,
  SQL state, or class name reach the client instead of the § 4.5 classification
  table's `extensions.classification`.
- **Secrets**: hardcoded credentials, tokens, or connection strings introduced
  in the diff (including test fixtures and `application*.yaml`).
- **Input validation at the boundary**: a GraphQL input value used before the
  value object that should validate it (range, format) is constructed.

---

## Step 4 — Report

Call `ReportFindings` exactly once, most severe first. Use `category`:
`correctness` for logic findings, `architecture-drift` for Step 3's second
group, `security` for the third. Leave `verdict` unset — this is an inline
review, not a verify pass over someone else's findings.

If nothing survives review, call `ReportFindings` with an empty array and state
the scope that was actually checked (base SHA, file count) — an empty result
with no stated scope is indistinguishable from a review that didn't happen.

---

## Anti-patterns

- Reporting a style preference or "could be cleaner" as a finding — only a
  concrete failure scenario or a cited rule counts.
- Guessing a `file:line` from a filename or diff hunk header instead of reading
  the file.
- Silently skipping untracked new files because `git diff` didn't show them.
- Reviewing the whole repository when the scope is a diff — stay inside the
  resolved scope from Step 1.
- Re-deriving `ddd-hex-reviewer`'s full seven-group sweep instead of the
  condensed checklist above — that duplicates work the loop already pays for
  elsewhere.
- Truncating coverage on a large diff without saying so in the report.
