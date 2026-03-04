package com.dominikgaller.alpinebooking.booking.core.domain.event;

/**
 * Marker interface for all domain events in the booking context.
 *
 * <p>Implementing types are immutable records that describe facts
 * which have occurred in the domain (named in past tense).
 *
 * <p>Framework-free: no Spring, no IO.
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}.
 */
public interface DomainEvent {
}
