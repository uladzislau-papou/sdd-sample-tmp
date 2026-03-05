package com.dominikgaller.alpinebooking.booking.core.outport;

import com.dominikgaller.alpinebooking.booking.core.domain.GuideTour;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourId;

import java.util.Optional;

/**
 * Outbound port for persisting the {@link GuideTour} aggregate (write side).
 *
 * <p>The implementation must persist each operation atomically.
 *
 * <p>Framework-free: implementations live in {@code outbound.persistence.write}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
public interface GuideTourRepository {

    /**
     * Persists a new {@link GuideTour} aggregate.
     *
     * @param guideTour the aggregate to persist; must not be null
     */
    void save(GuideTour guideTour);

    /**
     * Loads a {@link GuideTour} aggregate by its identity.
     *
     * @param guideTourId the guide tour identity; must not be null
     * @return the aggregate if found, or {@link Optional#empty()} otherwise
     */
    Optional<GuideTour> findById(GuideTourId guideTourId);

    /**
     * Persists a state change on an existing {@link GuideTour} aggregate.
     *
     * @param guideTour the aggregate whose current state should be written; must not be null
     */
    void update(GuideTour guideTour);
}
