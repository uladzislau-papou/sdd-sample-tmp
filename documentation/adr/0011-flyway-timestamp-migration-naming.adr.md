# ADR 0011 – Timestamp-Prefixed Flyway Migration Naming

## Status

Accepted (retroactive)

## Context

Schema changes are managed with Flyway, applied on startup. The original convention was
sequential: `V1__create_identifications.sql` through `V27__add_kyc_case_status.sql`.

Sequential numbering fails as soon as more than one branch is open. Two developers both
take `V28`; whichever merges second must rename their file. If both have already been
applied in a shared environment, the rename produces a checksum mismatch and Flyway
refuses to start.

This happened often enough to be a recurring cost rather than an occasional annoyance.

## Decision

**All new migrations use a UTC timestamp version prefix:**

```
VYYYYMMDDHHmm__snake_case_description.sql
```

with a verb-led description: `create_`, `add_`, `alter_`, `rename_`.

```
V202607081000__add_kyc_partner_number.sql
V202608312130__scope_functionary_documents_to_company.sql
```

Rules:

1. **Never add a new sequential `V<n>__` migration.** `V1`–`V27` remain as-is for Flyway
   history and are never renamed.
2. **Never edit a migration already applied in any shared environment.** Add a new one.
3. One logical schema change per file.
4. Pick a timestamp that sorts after existing migrations when working in parallel.
5. No manual schema changes in any environment; no Hibernate `ddl-auto` beyond `validate`.
6. A migration adding a NOT NULL column to a populated table supplies a default or
   backfills in the same file.
7. No `baseline-on-migrate` without an ADR.

## Consequences

**Positive**

- Two branches essentially never collide: a collision requires the same UTC minute.
- Merge order does not matter. A migration authored earlier but merged later still applies
  in timestamp order, which is usually the intent.
- The filename carries when the change was written, which is useful context when reading
  history.

**Negative / accepted**

- **Two conventions coexist forever.** `V1`–`V27` sort before every timestamped file,
  which happens to be correct, but a reader sees an inconsistent directory. Renaming the
  legacy files is not an option — their checksums are recorded in applied environments.
- **Timestamp order is not merge order.** A migration written on the 1st and merged on the
  20th applies before one written on the 10th and merged on the 12th. If they touch the
  same object, the result depends on which environment applied what and when. The
  one-logical-change rule and review are the mitigation; nothing enforces it.
- **A hand-typed timestamp can be wrong.** Nothing validates that the prefix is a real UTC
  time or that it sorts after existing files. This is a review check.
- Long filenames.

## Constraints

- **Changing the migration convention is an ADR trigger** (`sdd.playbook.md` § 6 item 4).
- A migration that changes existing data has an integration test proving the effect
  (`test.definition.md` § 6).
- Editing an applied migration is a merge blocker (`test.definition.md` § 7 gate 15).

## Alternatives considered

- **Keep sequential numbering with a merge-time renumbering step.** Rejected — that is the
  cost this decision removes, and it is unsafe once a branch has been deployed to a shared
  environment.
- **Retroactively renumber `V1`–`V27` to timestamps.** Rejected — the checksums are
  recorded in every applied environment; renaming would require a repair on each.
- **Flyway's `out-of-order` mode with sequential numbers.** Rejected — it permits gaps to
  fill in later, which makes the applied order genuinely unpredictable rather than merely
  non-obvious.
