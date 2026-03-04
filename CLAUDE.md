# CLAUDE.md – Alpine Booking

## Project

Alpine Booking is a reference-grade Tour Booking backend.
See [`documentation/project.definition.md`](documentation/project.definition.md) for vision, purpose, and non-goals.

---

## Documentation Authority Order

Rules are defined in the following files, ordered by precedence (highest first):

1. [`documentation/project.definition.md`](documentation/project.definition.md) – Vision, strategic intent, non-goals
2. [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – Layering, package ontology, Ports & Adapters rules
3. [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – Java coding style, naming conventions, class roles
4. [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – DDD building blocks, Always-Valid doctrine
5. [`documentation/technical.spec.md`](documentation/technical.spec.md) – Tech stack, tooling, persistence strategy
6. [`documentation/test.definition.md`](documentation/test.definition.md) – Test taxonomy, assertion rules, quality gates
7. [`documentation/sdd.playbook.md`](documentation/sdd.playbook.md) – SDD governance, ADR triggers, quality gate principles
8. [`documentation/execution.playbook.md`](documentation/execution.playbook.md) – Step-by-step execution loop, output contract
9. [`documentation/domain-vs-use-case.definition.md`](documentation/domain-vs-use-case.definition.md) – Responsibility boundary clarification
10. [`documentation/adr/`](documentation/adr/) – Architectural Decision Records
11. [`documentation/domain/`](documentation/domain/) – Concrete domain specs
12. [`documentation/use-cases/`](documentation/use-cases/) – Concrete use case specs
13. [`documentation/notes.md`](documentation/notes.md) – Scratchpad (non-authoritative)

Higher documents override lower ones.
See [`documentation/file-usage.definition.md`](documentation/file-usage.definition.md) for the full governance model.

---

## How to Work on This Project

Before any implementation, follow the execution loop defined in [`documentation/execution.playbook.md`](documentation/execution.playbook.md):

1. Read Phase – understand domain impact
2. Spec Phase – create/update specs and ADRs as required
3. Plan Phase – task list, risks, acceptance criteria
4. Implement Phase – smallest safe increment
5. Verify Phase – `./gradlew clean test` and `./gradlew build`
6. Closeout Phase – structured completion report

**No implementation without a spec. No architectural change without an ADR.**

### Always-Read Documents

The following documents apply to **every** implementation task and MUST be read before writing any code, regardless of the task scope:

- [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – package structure, layering rules, dependency directions
- [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – naming conventions, class roles (`*RestAPI`, `*Controller`, `*Driver`, …), visibility rules
- [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – Always-Valid doctrine, DDD building blocks

---

## REST Endpoint Documentation

Every implemented REST endpoint **must** have a corresponding JetBrains HTTP Client file in `rest/`.

- One file per use case, named `uc<nn>-<use-case-name>.http`
- Each file must cover: the happy-path request, and one request per documented error case (400, 409, 502, etc.)
- Files are updated as part of the **Implement Phase** — not as an afterthought
- The `rest/` folder is the living contract between the backend and any HTTP client (Postman, IntelliJ, curl)