# Notes

Non-authoritative scratchpad. See `CLAUDE.md` for the documentation authority order.

---

## Questions to ask, grouped by who can answer

> **Still unanswered. Now also closed provisionally, which is not the same thing.**
>
> UC07 was implemented against **our own decisions**, recorded in its § 2 as `PD-01 … PD-11`,
> because the SDD machinery had never been exercised end to end and running it was the point.
> The status went `BLOCKED` → `SPECIFIED` on that basis and on no other.
>
> **This list is therefore still live.** Nothing below has been answered by the JCM project;
> the questions are still the ones to ask, in the same words. What changed is that there is
> now working code resting on a guess for each — so each answer that arrives is a change to
> the spec *and* to the implementation, and the matching `PD` block states which parts.
>
> Two of the eleven were ours rather than theirs and are genuinely closed: OQ 11 by an
> `inbound.graphql` row in `architecture.definition.md` § 8.1 with an ArchUnit rule keyed to
> it, and the shared-kernel identity question by `adr/0023`. Those are struck through below.
> OQ 12 was closed earlier by `adr/0022`.
>
> The risk to watch is the one `HANDOFF.md` § 5 names: **a summary is not a source**. A `PD`
> that loses its marking becomes a sourced requirement in one edit, and PD-10 is the one to
> watch — it is a small invented validation matrix sitting where the real one (MVP risk 1)
> is supposed to go.

Eleven questions blocked `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`
— every one of them touching its § 2, § 3, § 7 or § 9. They are written here **as they would
be asked**, so that this section can be pasted into a message or walked through in a call
without translation.

The spec is the authoritative statement of each one; this list is the addressing layer. When
an answer arrives, it goes into the spec — with the page and revision that settled it — the
matching `PD` block is deleted, and the code the `PD` names is changed.

### Before asking anyone: read the Miro board

The data model page links an external Miro board carrying the model diagram. Relationships,
cardinalities and **optionality** are the natural content of such a board, and OQ 1 and OQ 10
ask for exactly that. It has not been read — it is outside the Confluence scopes available
here.

**Two of the eleven may already be answered there.** Read it before escalating them.

### For the product owner / the JCM discovery team

1. **Which fields are mandatory when a master contract is created?** (OQ 1 — the primary
   blocker.) The data model page lists every field and its meaning but says nothing about
   optionality, and an Always-Valid aggregate cannot be built without knowing. If the answer is
   "it depends on the case", that is also an answer and we model it.
2. **Is a service agreement (DLV) required at creation, or attached later?** (OQ 2) The data
   model puts `service_agreement_id` on the contract's configuration; the MVP page lists the DLV
   link among the *display* items, which does not settle when the link is made.
3. **Are affiliated or supplementary contracts in scope for the MVP?** (OQ 4) The model carries
   `parent_mlc_id`, `mlc_parent_config_id` and `inheritance_mode` for the "Base/Affiliated"
   case. The MVP excludes *"alle anderen Unternehmensänderungen"* but never mentions group
   structures either way, so we would be guessing in either direction.
4. **Can one employer hold two active master contracts at the same time?** (OQ 5) If not, we
   need a uniqueness rule and an error for it; if yes, we need neither. Note that
   `parent_mlc_id` suggests related contracts are normal, which makes the question sharper.
5. **What is the initial status, and what is the full status list?** (OQ 7) The MVP page states
   the lifecycle as *"Aktiv → Beendet"* and carries the unresolved *"ToDo: welche Stati brauchen
   wir hier noch?"* against that very line. This one also blocks the LRV termination use case
   entirely.
6. **Who sets `activation_date` — is it supplied, or is it the moment of creation?** (OQ 8) A
   contract may be agreed on one date and take effect on another. Interacts with our own
   architecture rule: an inbound adapter may not accept a timestamp from the request, so if the
   date is supplied we need to know by which route.
7. **What makes a set of terms valid, field by field?** (OQ 10) Must `price_range_min` be at
   most `price_range_max`? May `credit_limit`, `return_quota_percentage` or
   `eligible_employees` be zero or negative? Is a currency required whenever a monetary field
   is present? This is the field-level half of the MVP's **risk 1** — the linkage rules that
   *"sind im Moment nicht in Navax abgebildet"*, with "build the matrix" as the recorded
   measure. Until it exists, every value we accept is unconstrained.

### For the architecture owners (the component view's authors)

8. **Who invokes the creation of a master contract, and what fills `owner`?** (OQ 9) The
   Backoffice UI is our sourced consumer, but no source says an administrator *creates* a
   contract: the MVP says the trigger is mocked and eventually AGO, and the component view has
   the onboarding service forwarding onboarding data "to support master-agreement creation". The
   answer decides whether `owner` is a team assignment supplied with the data, or the acting
   user — which we cannot record at all until authentication exists.
9. **Who owns the employer record, and do we verify it exists?** (OQ 3 and OQ 6) The component
   view routes partner data through the partner service to **RADar** and describes **Odoo** as
   the system of record reached for JobCyclist data. Two sub-questions: who issues
   `partner_number`, and do we check the employer exists at creation or trust the caller. The
   second decides whether this use case gets its first outbound call to a foreign system.
10. **`MLC` or `MLA`?** The component view names the services `MLA Management` / `ILA
    Management` (Agreement); the data model page, two weeks newer, names the entities
    `MASTER_LEASING_CONTRACT` / `INDIVIDUAL_LEASING_CONTRACT`. We followed the newer one, because
    every German source says *Vertrag*. If the platform settles on `MLA`, it is a rename across
    packages, specs and `api/` files — cheap now, expensive later.
11. **One deployable or two?** `adr/0016-single-deployable-for-the-mvp.adr.md` records our
    position, our reasons and the condition for revisiting it. The component view draws two
    services. That conversation has not happened, and the ADR is the artifact to bring into it.

### Also outstanding with them, not blocking UC07

- **The synchronisation strategy for Radar and Odoo.** The MVP's **risk 2** calls for it to be
  fixed *before the MVP starts*, with a proof exercise in both directions. It is not fixed.
  `adr/0019` and `adr/0022` fix the *mechanism* so the answer lands in one place; the mapping —
  which fields, to which system, on which event — is unanswered, and the first outbox row runs
  straight into it.
- **Roles and permissions.** The MVP's **risk 7**: no platform-wide concept exists, a workshop
  is pending. We verify a token and define no roles; inventing three would be inventing the
  wrong three.
- **What the onboarding service sends.** AGO's message format is documented nowhere, so we
  describe the inbound port from the domain's needs and do not guess at it. The question to
  carry into that conversation: does onboarding supply everything a valid contract needs, or
  must part be fetched?

### Ours to resolve, not theirs

Three of the four below are now done. They are kept rather than deleted because each records
*why* the gap existed, and the cause was the same in all three: a rule written when REST was
the only transport, never revisited when `adr/0020` made GraphQL the only one.

- ~~**OQ 11 — a gap in our own architecture definition.**~~ `architecture.definition.md` § 8.1's
  timestamp table enumerates `inbound.rest`, `inbound.listener` and a call from another
  context's driver. **GraphQL is not in it**, and GraphQL is now the only transport
  (`adr/0020`). The rule's discriminator is *the caller*, which puts a GraphQL client in the
  same category as a REST client — but that is a reading, and an ArchUnit rule keyed on
  `inbound.rest` would not enforce it. Needs a row, and the rule keyed to it.
  **Done:** the row exists, and `TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes`
  is keyed to both adapter packages rather than to `inbound.rest` alone.
- ~~**A second shared-kernel identity is reserved to an ADR.**~~ `adr/0005`'s Consequences state
  that `TourId` "is the only member, and adding a second should require an ADR". Making
  `EmployerId`, `LessorId` or `PartnerNumber` a shared Value Object therefore needs one — three
  times over. Related: `adr/0017`'s own wording describes these category‑3 identities in
  category‑2 language ("held as an opaque identity value"), which is a standing trap for the
  next reader.
  **Done:** `adr/0023` decides *not* to promote them — ADR-0005's category‑3 test needs a
  second context referencing them, and `ilc` does not exist. The `adr/0017` wording trap is
  named in that ADR's Context so the next reader meets it with the answer attached.
- ~~**The template has no status for "specified but not workable".**~~
  `use-case.spec.template.md` offers `SPECIFIED | IMPLEMENTED | SUPERSEDED`, and `/loop-uc`'s
  preflight refuses on a missing spec, a missing § 10, an uncheckable criterion and
  `SUPERSEDED` — never on unanswered questions. So UC07's blocked state is legible to a human
  and invisible to the loop. A `BLOCKED` status plus a preflight gate would fix it; that is an
  edit to the template and the skill.
  **Done:** both. The preflight reports `HALTED (spec BLOCKED)` and not `BLOCKED`, because that
  word was already step 6's no-progress terminal state and the two are opposite findings —
  "the loop never started" versus "the loop ran and could not move".
- ~~**The template's Bounded Context line has no GraphQL vocabulary**~~ either — it enumerated
  "REST by external client | event-driven | synchronous outport call". Same class of omission as
  the timestamp table, same cause. **Done** in the same edit as the `BLOCKED` status.

---

## Live debts — ours

### Owed: the read-side decision

The MVP is substantially about displays — conditions and limits, the contract PDF, the
leases under a master contract. This repository has no read side at all: no query ports, no
projections. `project.definition.md` records the absence, and the first display use case
must write the ADR that chooses the pattern.

It is a written debt rather than a blocker because the three use cases sequenced first are
all writes. It becomes urgent the moment a fourth is picked.

### Owed: authentication

`adr/0021-operational-baseline-from-the-platform.adr.md` adopts the platform's operational
tooling but deliberately stops short of authentication. Verifying a JWT is **production
code**, and the rule here is that production code arrives behind a failing test and a
specification — so it gets its own increment and its own ADR rather than riding along with
a dependency block.

Until then every GraphQL operation is unauthenticated. That is fine for an empty service and
is not fine the moment a real contract exists in a shared environment.

### Undecided: branch-name and commit-message validation

`risk-management-service`'s lefthook config validates both against a ticket key —
`feat|fix|test/<KEY>-<n>` for branches, `<type>: (<KEY>-<n>) <subject>` for messages. Those
two hooks were **not** adopted, and the reason is worth stating rather than leaving as an
omission.

There is no ticket key to validate against. Specifications here are derived from Confluence
pages in the `JCM` space, not from Jira issues, and the MVP page's planned "Jira structure
with the use case as the top level" does not exist yet. A hook demanding a key that cannot
be supplied gets bypassed with `--no-verify` on its second run, and a bypassed hook teaches
that hooks are advisory.

When a Jira project for Contract Management exists, both hooks are worth adopting verbatim.

### Never dispatched: the review agents

`ddd-hex-reviewer`, `conformance-reviewer` and `spec-documenter` have never run. The
architecture claims rest on the 27 ArchUnit rules, which pass, and not on the adversarial
review meant to sit above them.

This was a decision, not an oversight: running them on the inherited tour example would
have exercised the agents against a domain that is leaving. The cost is that their first
real run happens on Contract Management code — so if an agent's prompt is stale, that is
discovered while working on the domain rather than while working on the agent.

`/code-review`'s security axis cannot be exercised on the example at all: no authentication,
no PII, no outbound calls.

### Not registered yet: `mlc` and `ilc`

The registry in `architecture.definition.md` § 11 is parsed **in both directions** — a row
without a package on disk fails exactly as a package without a row does. So the two contexts
are decided (`adr/0015-two-contexts-by-contract-level.adr.md`) but not registered; the rows
land in the same increment that creates the packages.

### detekt is pinned to an alpha

`dev.detekt` `2.0.0-alpha.2`, needed for Kotlin 2.3. Its configuration schema has already
moved relative to 1.x — the key `complexity > LongParameterList > constructorThreshold`
exists in 1.x and does not exist here, and detekt **fails the build on an unknown property**
rather than ignoring it. Verify a key before adding it.

Resolved by consistency rather than by preference: `risk-management-service` runs the same
alpha, and this repository is no longer a template whose choice every generated service
would inherit.

### No integration test for `findConfirmedByTourId`

Exercised only through `TourStartedListenerTest`'s mocks. Belongs to the tour example and
leaves with it — recorded so that the absence is not mistaken for coverage in the meantime.
Noted in `ports/tour-booking-repository.outport.spec.md` § 3.

---

## Tripwire: provenance

The method here was written by **Dominik Galler** and the original carries no licence file,
which by default means all rights reserved. The current justification for this copy is
*personal reuse, not distributed*.

That justification stops being true on a specific event, not on a date:

- the first push to a repository owned by the client or by an organisation, or
- the first handover of this code to a team or a client as deliverable work.

Either one makes it a distribution. The cheap fix is one message to the author before that
happens; the expensive version is discovering it after a team's history is interleaved with
the inherited documents.

Deliberately recorded as a tripwire rather than a task, because there is nothing to do until
one of those two events is imminent.

---

## Closed, with the measurement

Kept rather than deleted: an absence nobody wrote down reads as verified.

### The outbox's transaction question, answered

`adr/0019` required the outbox row to be written in the contract's transaction while citing
`adr/0002` for after-commit publication. Those could not both hold: ADR‑0002's reference
adapter delivers via `@TransactionalEventListener(AFTER_COMMIT)`, so an appender subscribed
that way writes the row *after* the commit and loses the atomicity the outbox exists for.

Decided in `adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md`: the
driver's call site is unchanged, the adapter behind `DomainEventPublisher` writes the row
synchronously in the ambient transaction, and a separate scheduled relay dispatches. ADR‑0002's
status is unchanged — its *decision* was never in conflict, only its reference adapter, and it
had named this substitution in its own Future Considerations.

**How the defect got in, which is the part worth keeping.** `adr/README.md` summarised ADR‑0002
in one line as "Domain events are published after commit", collapsing the call site and the
delivery moment. ADR‑0019 was written against that summary rather than the ADR. The index's
summaries now describe decisions in the ADR's own terms — a summary is not a source, and this
is the same failure class as the three lists `CLAUDE.md` single-sources.

### The two integration tests now run

`TourBookingJpaRepositoryIT` and `GuideTourJpaRepositoryIT` had never executed — the
authoring machine had no Docker daemon. They now pass: **8 tests, 0 failures**, against real
PostgreSQL under `ddl-auto: validate`.

The note that stood here expected real findings, on the grounds that these tests are the only
check that the Flyway migrations and the JPA mappings agree. There were none. The migrations
and the mappings do agree, and that is now a measured fact.

Full gate at the same commit: 20 classes / 123 tests, 8 integration tests, `detektMain`,
`detektTest`, `spotlessCheck`.

### The documentation gates now fire on documentation changes

`ContextRegistryTest`, `SpecCitationsTest` and `DocumentationLinksTest` read
`documentation/`, which Gradle had no way to know — so a documentation-only change left
`test` UP-TO-DATE and the three gates written to catch documentation rot did not run.
`documentation/` and `api/` are now declared inputs of the `test` task, and the fix was
verified the way the repository verifies gates: by touching a document and watching the task
stop being up-to-date.

### `plan.md` and `tasks.md` no longer hold the transformation

They were reset from their templates once the transformation was in history — the condition
the deferral was waiting on.
