package com.example.service.shared.domain.event

/**
 * Marker interface for all domain events across all bounded contexts.
 *
 * Implementing types are immutable data classes describing facts that have occurred in
 * the domain, named in the past tense.
 *
 * Framework-free: no Spring, no IO.
 *
 * SDD: see `documentation/architecture.definition.md`, section 9 (Shared Kernel).
 */
interface DomainEvent
