package com.dominikgaller.alpinebooking.shared.domain.event;

/**
 * Marker interface for all domain events across all bounded contexts.
 *
 * <p>Implementing types are immutable records that describe facts
 * which have occurred in the domain (named in past tense).
 *
 * <p>Framework-free: no Spring, no IO.
 *
 * <p>SDD: See {@code documentation/architecture.definition.md}, section 9 (Shared Kernel).
 */
public interface DomainEvent {
}
