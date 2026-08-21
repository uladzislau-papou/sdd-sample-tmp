-- UC11 CompleteTour: GuideTour gains a completion timestamp.
-- Nullable, because a tour is not completed until it is.
-- SDD: documentation/use-cases/uc11-complete-tour.spec.md section 6.
ALTER TABLE guide_tour
    ADD COLUMN completed_at TIMESTAMP NULL;
