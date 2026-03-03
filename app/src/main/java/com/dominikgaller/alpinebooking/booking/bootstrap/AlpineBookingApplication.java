package com.dominikgaller.alpinebooking.booking.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Alpine Booking application.
 *
 * <p>Scans the entire {@code booking} bounded-context package. All adapters,
 * stubs, and wiring are resolved from this root via {@link BookingConfig}.
 *
 * <p>SDD: See {@code documentation/architecture.definition.md}, section 4.9.
 */
@SpringBootApplication(scanBasePackages = "com.dominikgaller.alpinebooking.booking")
public class AlpineBookingApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AlpineBookingApplication.class, args);
    }
}
