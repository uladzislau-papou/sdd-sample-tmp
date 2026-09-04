# Layer Responsibilities – Controller / Service / Entity

## Purpose

Clarifies the strict separation between the three layers that carry behaviour in RMS.

Confusion here produces the two failure modes this codebase is most prone to: business
logic drifting into controllers and mappers, and services that are transaction scripts
because the entity carries nothing.

This document introduces no new rules. It is the quick-reference form of
`architecture.definition.md` § 4 and `modelling.definition.md`.

---

## 1. Controller (`api/controller`)

**Answers: what did the caller ask for, and may they ask for it?**

Does:

- Bind and validate input (`@Valid` on an `api/input` type)
- Delegate to exactly one service method
- Map the result to a `*Dto`

Does NOT:

- Contain business rules or status checks
- Inject a repository
- Open a transaction
- Return or accept an entity
- Catch a domain exception to reshape it — that is `web/advice`'s job

## 2. Service (`service`)

**Answers: is this allowed, and what happens as a result?**

Does:

- Own the transaction
- Load entities via repositories
- Enforce transition legality
- Mutate entity state
- Persist
- Emit audit events, enqueue outbound deliveries
- Translate provider outcomes into our vocabulary
- Throw the typed exception the contract names

Does NOT:

- Know about HTTP, GraphQL, or the shape of a request
- Return an entity across the API boundary — that is the controller's mapping step
- Perform its own authentication (filters and interceptors do that) — though it MAY read
  `OperatorContext` for attribution

## 3. Entity (`domain/model`)

**Answers: what is true about this row?**

Does:

- Hold persistent state and its column mapping
- Expose derived read-only accessors computed from its own fields
- Carry `@Version` where concurrent status advancement is possible
- Document, in KDoc, what each property means and what `null` means

Does NOT:

- Load another row
- Call a repository, a client, or a scheduler
- Read the clock to make a decision
- Orchestrate anything

## 4. Mapper (`api/mapper`)

**Answers: what does this look like in the other vocabulary?**

Pure translation, no IO, no repository, no clock. The only place a provider's vocabulary
meets ours. Total: an unmapped case fails, it does not default.

## 5. Advice / Interceptor (`web/advice`)

**Answers: how does this failure reach the caller?**

Maps a typed exception to a status or GraphQL error classification. No business logic, no
personal data, no stack traces, no provider payloads.

---

# 6. Quick Rule of Thumb

| The logic answers… | It belongs in… |
|--------------------|----------------|
| "May this happen, given the current state?" | Service (or the state machine it calls) |
| "What is true about this single row?" | Entity |
| "In which order do we call things?" | Service |
| "What does the caller see?" | Controller + Mapper |
| "What does this provider's value mean to us?" | Mapper |
| "What status code does this failure produce?" | Advice |

---

# 7. Anti-Patterns

- A status comparison in a controller → wrong layer
- A repository in a mapper → it is a service
- An entity method that queries another table → wrong layer
- A service that returns an entity to a controller which returns it verbatim → the DTO
  boundary is missing
- An advice that decides what the business outcome should be → wrong layer
- A `@Transactional` on a controller → wrong layer, and probably a symptom of one of the
  above

Boundary clarity is mandatory.
