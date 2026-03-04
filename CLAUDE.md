# CLAUDE.md – Alpine Booking

## Project

Alpine Booking is a reference-grade Tour Booking backend.
See [`documentation/project.definition.md`](documentation/project.definition.md) for vision, purpose, and non-goals.

---

## Documentation Authority Order

Rules are defined in the following files, ordered by precedence (highest first):

1. [`documentation/project.definition.md`](documentation/project.definition.md) – Vision, strategic intent, non-goals
2. [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – Layering, package ontology, Ports & Adapters rules
3. [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – DDD building blocks, Always-Valid doctrine
4. [`documentation/technical.spec.md`](documentation/technical.spec.md) – Tech stack, tooling, persistence strategy
5. [`documentation/test.definition.md`](documentation/test.definition.md) – Test taxonomy, assertion rules, quality gates
6. [`documentation/sdd.playbook.md`](documentation/sdd.playbook.md) – SDD governance, ADR triggers, quality gate principles
7. [`documentation/execution.playbook.md`](documentation/execution.playbook.md) – Step-by-step execution loop, output contract
8. [`documentation/domain-vs-use-case.definition.md`](documentation/domain-vs-use-case.definition.md) – Responsibility boundary clarification
9. [`documentation/adr/`](documentation/adr/) – Architectural Decision Records
10. [`documentation/domain/`](documentation/domain/) – Concrete domain specs
11. [`documentation/use-cases/`](documentation/use-cases/) – Concrete use case specs
12. [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – Java coding style
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

---

## REST Endpoint Documentation

Every implemented REST endpoint **must** have a corresponding JetBrains HTTP Client file in `rest/`.

- One file per use case, named `uc<nn>-<use-case-name>.http`
- Each file must cover: the happy-path request, and one request per documented error case (400, 409, 502, etc.)
- Files are updated as part of the **Implement Phase** — not as an afterthought
- The `rest/` folder is the living contract between the backend and any HTTP client (Postman, IntelliJ, curl)