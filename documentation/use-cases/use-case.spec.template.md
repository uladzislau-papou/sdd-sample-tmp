# Use Case Specification – <UseCaseName>

## Status
SPECIFIED | IMPLEMENTED

## Purpose
Describe orchestration logic.


## 1. Intent

What business outcome does this use case produce?


## 2. Input Contract

Fields:
- fieldA
- fieldB

Validation rules:
- Required fields
- Format rules


## 3. Output Contract

Return type:
- Success payload
- Error type(s)


## 4. Preconditions

- Aggregate must exist?
- Must be in specific state?


## 5. Flow

1. Load aggregate
2. Call domain method
3. Persist aggregate
4. Publish event (if applicable)


## 6. Side Effects

- Persistence
- External calls
- Event publication


## 7. Acceptance Criteria

Given
When
Then


## 8. Failure Scenarios

- Aggregate not found
- Invalid state
- External dependency failure


## 9. Test Requirements

Must include:
- Happy path test
- Failure path test
- Persistence interaction verification