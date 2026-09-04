# File Naming Definition

Filenames are part of the contract: `/uc-to-plan uc03` resolves a spec by filename, and
the agents glob for these patterns. A misnamed file is an unfindable file.

All names are `kebab-case`. Numeric prefixes are zero-padded so lexicographic order equals
numeric order.

# Definitions

Definitions define core constraints and rules.

Named after the subject, followed by `.definition.md`:
`architecture.definition.md`, `layer-responsibilities.definition.md`

# Playbooks

Playbooks describe processes (SOPs).

Named after the process, followed by `.playbook.md`: `execution.playbook.md`

# Specifications

| Kind | Pattern | Example |
|------|---------|---------|
| Use case | `uc<nn>-<kebab-name>.spec.md` | `uc03-decline-kyc-case.spec.md` |
| Domain / entity | `entity-<kebab-name>.spec.md` | `entity-kyc-case.spec.md` |
| Inbound surface | `<kebab-name>.inbound.spec.md` | `kyc-case-decisions.inbound.spec.md` |
| Outbound integration | `<kebab-name>.outbound.spec.md` | `kycnow-screening.outbound.spec.md` |
| Template | `<kind>.spec.template.md` | `use-case.spec.template.md` |

Directories:

| Directory | Holds |
|-----------|-------|
| `documentation/domain/` | `entity-*.spec.md` |
| `documentation/integrations/` | `*.inbound.spec.md`, `*.outbound.spec.md` |
| `documentation/use-cases/` | `uc<nn>-*.spec.md` |

The `uc<nn>` number is stable once assigned. It is the traceability key linking
spec → `rest/uc<nn>-*.http` → tests.

**On "inbound"/"outbound" rather than "port".** RMS has no ports
(`architecture.definition.md` § 2.2). An inbound spec describes a surface the outside world
calls — a GraphQL operation set, a REST endpoint group, a provider webhook. An outbound
spec describes something RMS calls or delivers to — a provider client, an outbox delivery.
Using "port" here would name a construct that does not exist in the code.

# ADR

Pattern: `<nnnn>-<kebab-title>.adr.md` — a four-digit sequence number, the decision as a
kebab-case phrase, then `.adr.md`:

`0007-kyc-case-state-machine.adr.md`

Inside the file, the H1 is `# ADR <nnnn> – <Title>` using an en dash. ADRs are immutable
once accepted; superseding an ADR means writing a new one.

# HTTP Client Files

One file per use case in `rest/`, named to match its spec:

`uc<nn>-<kebab-name>.http` e.g. `rest/uc03-decline-kyc-case.http`

The `uc<nn>` prefix MUST match the corresponding use case spec. See `rest/README.md` for
the GraphQL request shape.

# Service-Repository Files

These conventions govern the **service** repository and are restated here because agents
create these files:

| Kind | Pattern | Authority |
|------|---------|-----------|
| Flyway migration | `VYYYYMMDDHHmm__snake_case_description.sql` | `technical.spec.md` § 5.1 |
| GraphQL schema | `src/main/resources/graphql/<module>/<name>.graphqls` | `technical.spec.md` § 2.1 |
| Test class | `<ProductionClass>Test` / `<ProductionClass>IntegrationTest` | `test.definition.md` § 5.2 |
| Kotlin class | `PascalCase.kt`, one public type per file, named after it | `coding-style.definition.md` § 4.1 |

# Executable Layer (`.claude/`)

| Kind | Pattern | Example |
|------|---------|---------|
| Subagent | `.claude/agents/<kebab-name>.md` | `.claude/agents/rms-architecture-reviewer.md` |
| Skill | `.claude/skills/<kebab-name>/SKILL.md` | `.claude/skills/loop-uc/SKILL.md` |
| Command | `.claude/commands/<kebab-name>.md` | `.claude/commands/uc-to-plan.md` |

The agent/skill/command name in the YAML frontmatter MUST equal the filename stem — that is
how it is invoked.
