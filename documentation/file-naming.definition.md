# File Naming Definition

Filenames are part of the contract: `/uc-to-plan uc05` resolves a spec by
filename, and the agents glob for these patterns. A misnamed file is an
unfindable file.

All names are `kebab-case`. Numeric prefixes are zero-padded so that
lexicographic order equals numeric order.

# Definitions
Definitions define core constraints and rules.

Files are named after the definition they are based on, followed by `.definition.md` e.g.
`architecture.definition.md`

# Playbook
Playbooks describe processes and process definitions (SOPs).

Files are named after the playbook they are based on, followed by `.playbook.md` e.g.
`execution.playbook.md`

# Specification (Spec)
Specifications describe the requirements and constraints of a system.

Files are named after the specification they are based on, followed by `.spec.md`.

| Kind | Pattern | Example |
|------|---------|---------|
| Use case | `uc<nn>-<kebab-name>.spec.md` | `uc05-issue-individual-leasing-contract.spec.md` |
| Domain / aggregate | `aggregate-<kebab-name>.spec.md` | `aggregate-master-leasing-contract.spec.md` |
| Inbound port | `<kebab-name>.inport.spec.md` | `issue-individual-leasing-contract.inport.spec.md` |
| Outbound port | `<kebab-name>.outport.spec.md` | `master-leasing-contract-repository.outport.spec.md` |
| Template | `<kind>.spec.template.md` | `use-case.spec.template.md` |

The `uc<nn>` number is stable once assigned. It is the traceability key linking
spec → `graphql/uc<nn>-*.graphql` → tests.

**German abbreviations do not appear in filenames.** The spec for the master
leasing contract is `aggregate-master-leasing-contract.spec.md`, not
`aggregate-lrv.spec.md`. The abbreviation is ubiquitous language and belongs in
the spec's prose (`CLAUDE.md`, Ubiquitous Language); a filename has to be
guessable by someone who has not yet learned it, and `uc<nn>` already provides the
short handle.

# ADR
Architectural Decision Records capture architectural decisions made in the project.

Pattern: `<nnnn>-<kebab-title>.adr.md` — a four-digit sequence number, the
decision as a kebab-case phrase, then `.adr.md`. Example:

`0003-separate-individual-leasing-context.adr.md`

Inside the file, the H1 is `# ADR <nnnn> – <Title>` using an en dash.
ADRs are immutable once accepted; superseding an ADR means writing a new one.

# GraphQL Files

Two different kinds of GraphQL file, in two different places, and conflating them
is the mistake this section exists to prevent.

| Kind | Location | Pattern | Role |
|------|----------|---------|------|
| **Schema** | `src/main/resources/graphql/<context>/` | `<kebab-name>.graphqls` | The type contract. Loaded by the server; a resolver with no matching field fails at startup |
| **Request** | `graphql/` | `uc<nn>-<kebab-name>.graphql` | Worked examples of calling the API, including every documented failure |

Note the extensions differ by one character and this is Spring GraphQL's
convention, not ours: `spring.graphql.schema.locations` picks up `.graphqls`, and
a schema file saved as `.graphql` is silently not loaded. The request files use
`.graphql` so no client tool mistakes them for schema.

The `uc<nn>` prefix on a request file MUST match the corresponding use case spec.

# Executable Layer (`.claude/`)

| Kind | Pattern | Example |
|------|---------|---------|
| Subagent | `.claude/agents/<kebab-name>.md` | `.claude/agents/ddd-hex-reviewer.md` |
| Skill | `.claude/skills/<kebab-name>/SKILL.md` | `.claude/skills/loop-uc/SKILL.md` |
| Command | `.claude/commands/<kebab-name>.md` | `.claude/commands/uc-to-plan.md` |

The agent/skill/command name in the YAML frontmatter MUST equal the filename
stem — that is how it is invoked.

# Kotlin source files

One top-level declaration per file, named after it —
`MasterLeasingContract.kt` holds `MasterLeasingContract`. Kotlin permits several
top-level declarations per file and this project does not use that freedom, with
one exception: a value object may share a file with its companion factory, since
they are one concept. detekt's `MatchingDeclarationName` enforces the rule.

Sealed hierarchies are the usual reason to break this rule elsewhere. They do not
apply here — `DomainEvent` is deliberately not sealed
(`coding-style.definition.md` § 2.2).
