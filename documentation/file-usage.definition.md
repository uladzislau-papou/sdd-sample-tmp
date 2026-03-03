# File Usage Definition

## Purpose

This document defines the authoritative role of each file inside the `/documentation` directory.

It prevents rule duplication, circular governance, and specification drift.


# 1. Authority Order (Source of Truth)

The documents follow this strict precedence order:

1. project.definition.md
2. architecture.definition.md
3. modelling.definition.md
4. technical.spec.md
5. test.definition.md
6. sdd.playbook.md
7. execution.playbook.md
8. domain-vs-use-case.definition.md
9. ADRs (`/adr/*.adr.md`)
10. Concrete Specs (`/domain/*.md`, `/use-cases/*.md`)
11. coding-style.definition.md
12. notes.md

Higher documents override lower ones.

Templates do not override definitions.


# 2. File Responsibilities

## project.definition.md
Defines:
- Vision
- Strategic intent
- Non-goals
- Success criteria

No technical detail belongs here.


## architecture.definition.md
Defines:
- Layering
- Package ontology
- Ports & Adapters rules
- Framework boundary rules

Does NOT define domain modeling semantics.


## modelling.definition.md
Defines:
- Aggregates
- Entities
- Value Objects
- Domain Events
- Always-Valid doctrine

Does NOT define use case orchestration.


## technical.spec.md
Defines:
- Tech stack decisions
- Tooling constraints
- Database strategy
- Migration strategy
- SQL classification rules

Does NOT define governance or workflow.


## test.definition.md
Defines:
- Test taxonomy
- Assertion rules
- Coverage expectations
- Merge blockers

Does NOT redefine architectural layering.


## sdd.playbook.md
Defines:
- Spec hierarchy
- Governance discipline
- ADR triggers
- Quality gate principles

Does NOT describe step-by-step execution.


## execution.playbook.md
Defines:
- Operational execution loop
- Task workflow
- Verification steps
- Output contract

It MUST NOT redefine rules already defined in:
- technical.spec
- modelling.definition
- test.definition

It may reference them.


## domain-vs-use-case.definition.md
Clarifies responsibility boundaries.
Purely conceptual.
Does not introduce new rules.


## /domain/*.md
Concrete domain specifications.
Must follow `domain.spec.template.md`.


## /use-cases/*.md
Concrete use case specifications.
Must follow `use-case.spec.template.md`.


## /adr/*.adr.md
Records architectural decisions.
Immutable once accepted.


# coding-style.definition.md
Defines how the actual code should look like.
Does not introduce any architectural decisions or functionality.
Changes over time.


## notes.md
Scratchpad.
Non-authoritative.
May not contradict formal definitions.


# 3. Template Governance

Templates:
- `/domain/domain.spec.template.md`
- `/use-cases/use-case.spec.template.md`

Templates define structure, not rules.

Changing a template does not change governance.
Changing governance requires updating the relevant definition file.


# 4. No Duplication Rule

Rules MUST appear in exactly one authoritative file.

Execution playbook references rules.
SDD playbook defines governance.
Technical spec defines tooling.

Copy-paste duplication is forbidden.


# 5. Change Protocol

When modifying rules:

1. Identify highest authoritative file.
2. Update that file.
3. Remove duplicated rules from lower documents.
4. Document change in ADR if architectural.


# 6. Anti-Patterns

- Duplicating governance rules in multiple files
- Letting execution.playbook redefine architecture
- Letting templates introduce new constraints
- Treating notes.md as specification

Clarity over convenience.