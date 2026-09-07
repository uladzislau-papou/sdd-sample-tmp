package com.example.service.booking.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * Persistence row type for the `tour_booking` table.
 *
 * Lives in `outbound.persistence` and **never** in `core`. That is the one persistence
 * rule the template fixes, and it is enforced rather than agreed: `DependencyRulesTest`
 * fails if `jakarta.persistence` is reachable from `core`. The ORM is a project choice; the
 * domain staying free of it is not.
 *
 * The domain's aggregate and this class are mapped by [TourBookingMapper] and share no
 * types. That duplication is deliberate — it is what lets a column be renamed without
 * touching a domain invariant, and an invariant to change without a migration.
 *
 * `LongParameterList` is suppressed for the same reason it is on the aggregate: a row type
 * assembles a whole row in one constructor, so the count is a property of the table rather
 * than of a behaviour with too many knobs — which is what the rule is for, and what its
 * default threshold still catches everywhere else.
 *
 * SDD: see `documentation/ports/tour-booking-repository.outport.spec.md`.
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "tour_booking")
class TourBookingJpaEntity(
    @Id
    @Column(name = "id", nullable = false, length = 36)
    var id: String = "",
    @Column(name = "tour_id", nullable = false)
    var tourId: String = "",
    @Column(name = "tour_date", nullable = false)
    var tourDate: LocalDate = LocalDate.EPOCH,
    @Column(name = "participant_count", nullable = false)
    var participantCount: Int = 0,
    @Column(name = "available_capacity", nullable = false)
    var availableCapacity: Int = 0,
    @Column(name = "contact_name", nullable = false)
    var contactName: String = "",
    @Column(name = "contact_email", nullable = false)
    var contactEmail: String = "",
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "",
)
