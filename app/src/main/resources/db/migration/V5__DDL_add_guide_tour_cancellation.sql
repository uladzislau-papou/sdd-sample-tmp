-- UC12 CancelTourByGuide: record when a guide called a tour off, and why.
--
-- Both columns are nullable: a tour that was never cancelled has no values for them.
--
-- cancellation_reason is VARCHAR(400) to match the booking side's
-- CancellationReason.MAX_LENGTH. The guide context validates the same ceiling but does not
-- share the value object -- reason crosses to booking as a plain String through the inport
-- (architecture.definition.md section 11 rule 3), so the two contexts agree on the limit
-- without sharing the type. GuideTourJooqRepositoryIT pins this width against the constant.
ALTER TABLE guide_tour ADD COLUMN cancelled_at TIMESTAMP NULL;
ALTER TABLE guide_tour ADD COLUMN cancellation_reason VARCHAR(400) NULL;
