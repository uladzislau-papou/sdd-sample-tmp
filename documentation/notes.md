# Notes

Non-authoritative scratchpad. See `CLAUDE.md` for the documentation authority order. Where
this file and a document in `documentation/` disagree, that document wins and this one is
stale.

---

## What happened to the eleven questions

This file used to carry eleven open questions blocking a specification derived from an
external Confluence space, grouped by who could answer them. **They are gone, and not because
they were answered.** The scope they belonged to was removed: the leasing domain, its data
model, the specification resting on it, and the eight ADRs supporting it.

Recorded here because deleting a question list is exactly the kind of edit that later looks
like the questions were resolved. They were not. They were made irrelevant, which is a
different thing, and if that scope ever returns they return with it — unanswered, in the same
words, retrievable from `git log`.

The lesson from that episode outlived the scope and is worth keeping in front of whoever works
here next:

**A specification blocked on someone else's decision teaches you nothing about your method
while you wait.** Four rounds of `spec-reviewer` found real defects in that spec every time —
an unsourced actor, an invented validation rule filed as sourced, an open question referenced
four times and never written. That was genuinely useful. The eleven questions were not; they
were a queue for other people. The current domain is invented precisely so that the queue is
empty and the method is the only variable.

---

## Live debts

### Owed: authentication

Every GraphQL operation is unauthenticated. `adr/0021` adopted the platform's operational
baseline and deliberately stopped short of authentication, because verifying a JWT is
**production code** and the rule here is that production code arrives behind a failing test and
a specification.

Fine for a service with no data. Not fine the moment one runs anywhere shared.

It is the most substantial thing on this list, and it wants its own increment, its own spec and
its own ADR — not a dependency added to `build.gradle.kts` during someone else's work.

### Owed: nothing else, and that is new

Every earlier version of this file carried a standing ADR debt — the read side. `adr/0025`
pays it. There is no decision currently owed, which means the next ADR is triggered by work
rather than by backlog.

### Undecided: branch-name and commit-message validation

The donor service's lefthook config validates both against a ticket key —
`feat|fix|test/<KEY>-<n>` for branches, `<type>: (<KEY>-<n>) <subject>` for messages. Those two
hooks were **not** adopted, and the reason is worth stating rather than leaving as an omission.

There is no ticket key to validate against. Specifications here have no external tracker at
all. A hook demanding a key that cannot be supplied gets bypassed with `--no-verify` on its
second run, and a bypassed hook teaches that hooks are advisory.

Worth adopting verbatim if this service ever acquires a tracker.

### Never dispatched: three of the four review agents

`ddd-hex-reviewer`, `conformance-reviewer` and `spec-documenter` have **never run**. The
architecture claims rest on the ArchUnit rules, which pass, and not on the adversarial review
meant to sit above them.

This was a decision rather than an oversight, taken twice for the same reason: running them
against a domain that was leaving would have exercised the agents on code nobody would keep.
The cost is that their first real run happens on `contract` code — so if an agent's prompt has
gone stale, that is discovered at the least convenient moment. **Plan for findings on the first
increment rather than a rubber stamp.**

`spec-reviewer` has run, four rounds, and found something in every one.

### Never run: `/code-review`

Its security axis has never had a subject: no auth, no PII, no outbound calls. It becomes
meaningful once authentication exists.

---

## Traps that have cost time here

Kept because each was found the expensive way, and none is discoverable from a passing build.

**`detekt` is not the gate.** `check` depends on `detektMain` and `detektTest`, the
type-resolution variants, which find things the convenience task does not. The lefthook
pre-commit hook runs the correct pair for the same reason.

**detekt is pinned to `2.0.0-alpha.2`** and its config schema has moved from 1.x. It **fails
the build on an unknown property** rather than ignoring it. Verify a key before adding it.

**Spring Boot 4 split its test-slice annotations into per-module artifacts.** `@DataJpaTest`
needs `spring-boot-data-jpa-test`; `@AutoConfigureTestDatabase` needs `spring-boot-jdbc-test`.
The failure mode is an unresolved import, not a helpful message.

**A GraphQL schema must declare a `Query` root**, even with nothing to read. `schema.graphqls`
carries one infrastructure field, `apiVersion`, for exactly that reason — and `uc02` removes it
when the first real query arrives.

**Gradle build-script helper functions must live inside `doLast`.** Top-level functions cannot
be serialised into the configuration cache.

**The `test` / `integrationTest` split is intentional.** `test` needs nothing installed;
`integrationTest` needs Docker; `check` depends on both.

**Documentation is an input to the `test` task**, declared explicitly, because it once was not
— and the three documentation-reading gates therefore stayed `UP-TO-DATE` on
documentation-only changes. Tests written to catch documentation rot were silent on exactly the
commits they existed for. **If you add another folder those tests read, add it there too.**

**`git checkout -- <file>` on an unstaged rewrite destroys it.** A whole rewritten `notes.md`
was lost that way once, to a checkout intended to revert one appended newline. Stage first, or
revert precisely.

**A `FAILED` from `detektMain` can be a stale Gradle daemon.** One occurred immediately after a
plugin was added, passed on re-run, and a 354 MB heap dump later confirmed a JVM had died.
Re-run once on a clean daemon before believing a sudden static-analysis failure — and if you
find a heap dump, do not let `git add -A` sweep it in. `*.hprof` is worth adding to
`.gitignore`.

**Adding a bounded context is not a `mkdir`.** § 11 is parsed both ways, so the row and the
package must land in the same increment. `contract` is decided and **not yet registered**, on
purpose.

**Sixteen architecture rules currently check nothing.** They pass. See `adr/0026` before
reading a green build as coverage.

---

## Tripwire: provenance

The method here was written by **Dominik Galler**; the original carries no licence file, which
by default means all rights reserved. The justification for this copy is *personal reuse, not
distributed*.

That stops being true on a specific **event**, not on a date: the first push to a repository
owned by a client or an organisation, or the first handover of this code as deliverable work.
Either makes it a distribution. The cheap fix is one message to the author before that happens.

Recorded as a tripwire rather than a task because there is nothing to do until one of those two
events is imminent.
