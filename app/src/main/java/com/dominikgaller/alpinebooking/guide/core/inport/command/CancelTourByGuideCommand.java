package com.dominikgaller.alpinebooking.guide.core.inport.command;

/**
 * Input carrier for UC12 – CancelTourByGuide.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 *
 * <p>Carries no timestamp: this use case is reached over REST, and a REST driver reads the
 * cancellation time from {@code ClockPort} rather than accepting the caller's
 * ({@code architecture.definition.md} § 8.1).
 *
 * <p>{@code reason} is optional and may be null. When present it is turned into a
 * {@code guide.core.domain.guidetour.CancellationReason} by the driver, which is where the
 * non-blank and 400-character rules live — this context stores the reason, so it owns that
 * invariant and enforces it in the domain rather than in an application service. The
 * command itself carries a plain {@code String}: a command is a carrier, not a guard, and
 * a domain type must not appear on the published contract.
 * before anything is mutated; the guide context does not share {@code booking}'s
 * {@code CancellationReason} value object, because a domain type must not cross a context
 * boundary (§ 11 rule 3), so the two agree on the 400-character ceiling without sharing code.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}, section 2.
 */
public record CancelTourByGuideCommand(
        String guideTourId,
        String reason
) {
}
