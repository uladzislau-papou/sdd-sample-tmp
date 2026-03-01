# Domain Specification – <AggregateName>

## Purpose
Describe the domain object and its invariants.


## 1. Aggregate Root
Name:
Description:


## 2. Invariants (Always-Valid)

- Invariant 1:
- Invariant 2:
- Invariant 3:

Violations MUST result in exception.


## 3. State Model

Possible states:
- STATE_A
- STATE_B
- STATE_C

Allowed transitions:
- A → B
- B → C

Illegal transitions:
- C → A


## 4. Behavior

Public methods:
- methodA()
- methodB()

Each method MUST describe:
- Preconditions
- Postconditions
- Emitted events (if any)


## 5. Domain Events

List of events emitted:
- EventA
- EventB

Describe:
- Trigger condition
- Payload


## 6. Failure Scenarios

Describe expected failures:
- Invalid transition
- Invariant violation
- Business constraint violation


## 7. Test Requirements

Must include:
- Invariant tests
- Transition tests
- Failure tests
- Event emission tests