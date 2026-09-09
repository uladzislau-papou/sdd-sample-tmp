package com.example.contractmanagement.architecture

import com.tngtech.archunit.lang.ArchRule

/**
 * Permits an architecture rule to match no classes, **for as long as the service has no
 * bounded contexts** — and no longer.
 *
 * ArchUnit fails a rule whose input set is empty, which is the right default: a rule that
 * silently checks nothing is worse than no rule, because it reports green. Deleting the
 * `booking`, `guide` and `mlc` contexts in one increment left sixteen rules in exactly that
 * position — they describe `..inbound.driver..`, `..core.inport.command..` and packages that
 * no longer contain anything. Every one of them failed with "failed to check any classes".
 *
 * Three ways out were available and two of them were wrong. Setting `failOnEmptyShould=false`
 * in an `archunit.properties` disarms every rule in the build invisibly and permanently.
 * Deleting the rules throws away the enforcement the next context needs on its first day.
 * What is actually true is narrower than either: **these rules have no subject yet**, and
 * that is a fact about the service's current state rather than about the rules.
 *
 * So the allowance is named after the condition that justifies it, and the condition has an
 * executable owner. [EmptyServiceTripwireTest] asserts that the service still has zero
 * bounded contexts. The moment the first one is registered, that test fails and its message
 * says what to do: remove every call to this function and delete both files. The weakening
 * therefore cannot outlive the state that excuses it — which is `adr/0014`'s instruction
 * applied to the workaround rather than to the rule. It is the same shape `TimestampRulesTest`
 * used for its `StartTourRequest` exclusion — an allowlist guarded by a test asserting the
 * violation still existed. That guard fired when the tour example was deleted and both were
 * removed, so the pattern has already completed one full cycle here.
 *
 * **Grep for `whileTheServiceHasNoBoundedContexts` to find every site.** There is deliberately
 * one name to search for and no way to spell it accidentally.
 *
 * SDD: see `documentation/adr/0026-the-empty-service-is-a-transient-state.adr.md` and
 * `documentation/adr/0014-quality-gates-are-executable.adr.md`.
 */
fun ArchRule.whileTheServiceHasNoBoundedContexts(): ArchRule = allowEmptyShould(true)
