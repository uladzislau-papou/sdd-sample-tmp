package com.example.contractmanagement.booking.core.inport.usecase

import com.example.contractmanagement.booking.core.inport.command.MarkBookingActiveCommand
import com.example.contractmanagement.booking.core.inport.result.MarkBookingActiveResult

/**
 * Inbound port for UC06 — MarkBookingActive.
 *
 * Transitions a booking from `CONFIRMED` to `ACTIVE`. Idempotent: activating an
 * already-active booking is a no-op.
 *
 * Deliberately **not** exposed over any transport. Its caller is an event listener inside
 * the same context, which is why UC06 has no `api/` file and why its spec marks § 9 as not
 * applicable.
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md`.
 */
interface MarkBookingActiveUseCase {
    fun markActive(command: MarkBookingActiveCommand): MarkBookingActiveResult
}
