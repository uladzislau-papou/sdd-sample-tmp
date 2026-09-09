package com.example.contractmanagement.architecture

import com.tngtech.archunit.lang.ArchRule

/**
 * Permits an architecture rule to match no classes **while the `contract` vertical slice is
 * still being built** — and no longer.
 *
 * ArchUnit fails a rule whose input set is empty, which is the right default: a rule that
 * silently checks nothing is worse than no rule, because it reports green. UC01 is building
 * the first bounded context inside-out (`tdd.definition.md` § 3), so between the first value
 * object and the persistence adapter there is a window in which rules describing
 * `..inbound.driver..`, `..core.inport.command..`, `..core.outport..` and `..inbound.graphql..`
 * have nothing to check. What is true of them is narrow — **they have no subject yet** — and
 * that is a fact about how far the slice has got, not about the rules.
 *
 * **This replaces `whileTheServiceHasNoBoundedContexts`, and the rename is the point.**
 * That allowance was named for "the service has no bounded contexts", a condition which ended
 * the moment the `contract` row was registered — while the rules it covered stayed
 * subjectless for several iterations more. Worse, it also covered five rules describing
 * `inbound.rest` and `inbound.listener`, whose subject `adr/0020` forbids the service from
 * ever having, so its precondition bundled a transient future together with a permanent one
 * and could not honestly expire. Those five rules are now deleted rather than relaxed; these
 * remaining ones are relaxed under a condition that genuinely ends.
 *
 * The condition has an executable owner. [ContractSliceTripwireTest] asserts the slice is
 * still incomplete. The moment it completes, that test fails and its message says what to do:
 * remove every call to this function and delete both files.
 *
 * **Grep for `whileTheContractSliceIsIncomplete` to find every site.** There is deliberately
 * one name to search for and no way to spell it accidentally.
 *
 * SDD: see `documentation/adr/0027-a-rule-with-no-possible-subject-is-deleted-not-allowed-to-pass-empty.adr.md`
 * and `documentation/adr/0014-quality-gates-are-executable.adr.md`.
 */
fun ArchRule.whileTheContractSliceIsIncomplete(): ArchRule = allowEmptyShould(true)
