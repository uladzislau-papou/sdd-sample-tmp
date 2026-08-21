-- UC08 CancelBookingByUser: make cancellation attributable.
--
-- All three columns are nullable: a booking that was never cancelled has no values for
-- them, and back-filling a sentinel would be indistinguishable from a real cancellation.
--
-- cancellation_reason is VARCHAR(400) to match CancellationReason.MAX_LENGTH. The domain
-- is the enforcing side -- an over-long reason is rejected as a 400 before it reaches
-- persistence -- so this width is a backstop that keeps the schema honest about the
-- contract, not the primary guard.
ALTER TABLE tour_booking ADD COLUMN cancelled_at TIMESTAMP NULL;
ALTER TABLE tour_booking ADD COLUMN cancelled_by VARCHAR(10) NULL;
ALTER TABLE tour_booking ADD COLUMN cancellation_reason VARCHAR(400) NULL;
