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
spec → `api/uc<nn>-*` → tests, and `/loop-uc UC05` takes it as its argument.

**Assigning a number.** The next number is the highest existing one plus one, and
`/spec-create` re-checks that immediately before writing the file. Two people specifying
two tickets at the same time can still collide; the fix is to rename the file and its one
reference while the spec is still fresh, before it reaches `plan.md`. A locking scheme is
not worth building for that.

**Why a number and not the ticket key.** A ticket key would never collide and would carry
the traceability in the filename. It is rejected because the template must not require
Jira: the example use cases in this repository have no ticket, a service using Linear or
GitHub Issues has a different key shape, and the first thing a reader would meet is a
fabricated key. Traceability lives in the spec's § 1 `Source` field instead, where it can
honestly say `n/a`.

# ADR
Architectural Decision Records are documents that capture architectural decisions made in the project.

Pattern: `<nnnn>-<kebab-title>.adr.md` — a four-digit sequence number, the
decision as a kebab-case phrase, then `.adr.md`. Example:

`0003-separate-guide-bounded-context.adr.md`

Inside the file, the H1 is `# ADR <nnnn> – <Title>` using an en dash.
ADRs are immutable once accepted; superseding an ADR means writing a new one.

# Executable API Requests

One file per use case **per transport**, in `api/`, named to match its spec:

| Transport | Pattern | Example |
|-----------|---------|---------|
| REST | `api/uc<nn>-<kebab-name>.http` | `api/uc05-start-tour.http` |
| GraphQL | `api/uc<nn>-<kebab-name>.graphql` | `api/uc01-request-tour-booking.graphql` |

The `uc<nn>` prefix MUST match the corresponding use case spec. A use case has a file per
transport it is actually exposed over, and **none** if it has no external API — a
listener-driven use case says so in its § 9 and has no file here.

The folder is `api/`, not `rest/`: the stack carries two transports, and a folder named
after one of them would have left the other's contract with nowhere to live.

These files are the living contract between the backend and any client. They are also the
cheapest check that a spec's § 9 and the code still agree, which is why `spec-documenter`
verifies them on every increment rather than treating them as documentation.

# Executable Layer (`.claude/`)

| Kind | Pattern | Example |
|------|---------|---------|
| Subagent | `.claude/agents/<kebab-name>.md` | `.claude/agents/ddd-hex-reviewer.md` |
| Skill | `.claude/skills/<kebab-name>/SKILL.md` | `.claude/skills/loop-uc/SKILL.md` |
| Command | `.claude/commands/<kebab-name>.md` | `.claude/commands/uc-to-plan.md` |

The agent/skill/command name in the YAML frontmatter MUST equal the filename
stem — that is how it is invoked.
