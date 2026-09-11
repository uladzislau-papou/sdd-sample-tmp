# Domain Specification – <AggregateName>

## Purpose
Describe the domain object and its invariants. Name the German term the business
uses for it (`CLAUDE.md`, Ubiquitous Language).


## 1. Aggregate Root
Name:
Description:
Consistency boundary: what is inside, what is referenced by id and why
(`modelling.definition.md`, Aggregate).


## 2. Invariants (Always-Valid)

Each invariant carries a stable `I-NN` identifier so tests and specs can cite it.

- **I-01**: <invariant> → throws `<Exception>`
- **I-02**:
- **I-03**:

Violations MUST result in an exception. Name the exception, not just the failure.


## 2a. Value Objects

For each: what it wraps, its invariants, the exception it throws, and — for an
identity — which of the four categories in `modelling.definition.md` § Identity it
falls into and therefore where it lives.


## 3. State Model

Possible states:
- STATE_A
- STATE_B

Allowed transitions:
- A → B

Illegal transitions:
- B → A

Where a transition's legality depends on *who* is performing it, give the guard as
a table rather than as prose.


## 4. Behavior

Public functions, with for each:
- Preconditions (and the exception each violation throws)
- Postconditions
- Emitted events
- Idempotency, where the function has any

State which properties are `var` and therefore mutable, because every one of them
must be written by the repository's `update`
(`architecture.definition.md` § 4.6, and the port spec).


## 5. Domain Events

| Event | Trigger | Payload | Package |
|-------|---------|---------|---------|
| `<Event>` | `<function>` | … | `<context>.core.domain.<aggregate>.event` or `shared.domain.event` |

Placement is decided by who consumes the event, not who emits it
(`modelling.definition.md`, Domain Event).


## 6. Failure Scenarios

| Scenario | Exception | Thrown by |
|----------|-----------|-----------|
| … | … | … |


## 7. Test Requirements

Expressed as checkable items naming the test that satisfies each.

### Covered
- [ ] Creation invariants — `<Test>.<method>`
- [ ] Value object invariants — `<Test>`
- [ ] Each transition and its guard — `<Test>.<method>`
- [ ] Event emission and drain semantics — `<Test>.<method>`
- [ ] Persistence round-trip of every mutable property — `<AdapterIT>`

### Open
- [ ] <known gap, with what would close it>
