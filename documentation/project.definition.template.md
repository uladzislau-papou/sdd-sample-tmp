# Project Vision: <ServiceName>

<!--
THIS IS THE BLANK A NEW SERVICE FILLS IN.

`initService` copies this over `project.definition.md`. What was there before defined the
tour-booking example that came with the template; it does not describe your service and
must not be left in place.

This is the **highest-ranked document** in `CLAUDE.md`'s authority order. Every agent run
reads it first. Two consequences:

  - Vagueness here is expensive. "Build a great service" gives an agent nothing to rank
    competing changes by, and it will rank them by something you did not choose.
  - Non-Goals earn their keep. An absence that is not written down gets invented — and
    invented differently each time. Most of the value of this file is in that last section.

Delete every comment block as you fill it in. A template comment left in a live document is
how a reader learns to stop trusting the document.
-->

## Purpose

<!-- What business outcome does this service exist to produce? Two or three sentences.
     Not the technology. Not the architecture. What breaks if this service does not
     exist. -->


## Problem

<!-- What makes this domain harder than it looks? Name the specific difficulties — the
     constraints, the lifecycles, the concurrency, the integrations. This section is what
     later justifies the rules you adopt; without it they read as ceremony. -->


## Core Focus

<!-- What this service models, as a short list. Equally: what sits just outside it and
     belongs to another service. -->


## Architectural Stance

<!-- Inherited from the template unless you say otherwise:

     - Strict Ports & Adapters
     - A framework-free core, enforced by ArchUnit rather than requested
     - Aggregates enforce their invariants; no setters
     - One use case = one transaction boundary, owned by the driver
     - Domain events published after commit
     - Contexts communicate through shared events or a published inport, never internals
     - No persistence annotations in the domain (ADR-0011)

     A departure from any of these is an ADR, not an edit here. Keep this section to the
     stances that are specific to your service. -->


## Development Doctrine

<!-- Inherited: spec first, ADR before architectural change, no production code without a
     failing test, small verifiable increments. Add what is specific to you. -->


## Non-Goals

<!-- The most valuable section in this document. Write down what this service deliberately
     does not do, and — for each — the consequence a reader should know about.

     Start from the template's own non-goals and decide each one explicitly, because the
     example ships without all of them:

       - a read side (query ports, projections, CQRS)
       - authentication and authorization
       - PII and secret handling
       - optimistic locking
       - real outbound integrations
       - observability: metrics, tracing, audit trail

     For each: either it is in scope and needs a spec and probably an ADR, or it is out of
     scope and belongs in the table below. Silence is the one answer that will cost you,
     because it gets resolved by whoever touches the code next. -->

| Absent | Consequence you should know about |
|--------|-----------------------------------|
|        |                                   |


## What "done" means

<!-- The exit condition for this service, at project level. Concrete enough that somebody
     other than you can tell whether it has been reached. -->
