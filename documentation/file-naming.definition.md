# File Naming Definition

Filenames are part of the contract: `/uc-to-plan uc03` resolves a spec by
filename, and the agents glob for these patterns. A misnamed file is an
unfindable file.

All names are `kebab-case`. Numeric prefixes are zero-padded so that
lexicographic order equals numeric order.

# Definitions
Definitions define core constraints and rules.

Files are named after the definition they are based on, followed by `.definition.md` e.g.
`architecture.definition.md`

# Playbook
Playbooks are describing processes and process definitions (SOPs)

Files are named after the playbook they are based on, followed by `.playbook.md` e.g.
`execution.playbook.md`

# Specification (Spec)
Specifications are describing the requirements and constraints of a system.

Files are named after the specification they are based on, followed by `.spec.md`.

| Kind | Pattern | Example |
|------|---------|---------|
| Use case | `uc<nn>-<kebab-name>.spec.md` | `uc05-start-tour.spec.md` |
| Domain / aggregate | `aggregate-<kebab-name>.spec.md` | `aggregate-tour-booking.spec.md` |
| Inbound port | `<kebab-name>.inport.spec.md` | `request-tour-booking.inport.spec.md` |
| Outbound port | `<kebab-name>.outport.spec.md` | `tour-booking-repository.outport.spec.md` |
| Template | `<kind>.spec.template.md` | `use-case.spec.template.md` |

The `uc<nn>` number is stable once assigned. It is the traceability key linking
spec → `rest/uc<nn>-*.http` → tests.

# ADR
Architectural Decision Records are documents that capture architectural decisions made in the project.

Pattern: `<nnnn>-<kebab-title>.adr.md` — a four-digit sequence number, the
decision as a kebab-case phrase, then `.adr.md`. Example:

`0003-separate-guide-bounded-context.adr.md`

Inside the file, the H1 is `# ADR <nnnn> – <Title>` using an en dash.
ADRs are immutable once accepted; superseding an ADR means writing a new one.

# HTTP Client Files
One file per use case in `rest/`, named to match its spec:

`uc<nn>-<kebab-name>.http` e.g. `rest/uc05-start-tour.http`

The `uc<nn>` prefix MUST match the corresponding use case spec.

# Executable Layer (`.claude/`)

| Kind | Pattern | Example |
|------|---------|---------|
| Subagent | `.claude/agents/<kebab-name>.md` | `.claude/agents/ddd-hex-reviewer.md` |
| Skill | `.claude/skills/<kebab-name>/SKILL.md` | `.claude/skills/loop-uc/SKILL.md` |
| Command | `.claude/commands/<kebab-name>.md` | `.claude/commands/uc-to-plan.md` |

The agent/skill/command name in the YAML frontmatter MUST equal the filename
stem — that is how it is invoked.
