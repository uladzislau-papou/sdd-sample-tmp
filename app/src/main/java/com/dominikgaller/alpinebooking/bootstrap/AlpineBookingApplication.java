package com.dominikgaller.alpinebooking.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Alpine Booking application.
 *
 * <p>Lives outside any bounded-context package. Scans the shared root
 * {@code com.dominikgaller.alpinebooking} so all bounded contexts and this
 * bootstrap package are discovered automatically.
 *
 * <p>SDD: See {@code documentation/architecture.definition.md}, section 4.9.
 */
@SpringBootApplication(scanBasePackages = "com.dominikgaller.alpinebooking")
public class AlpineBookingApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AlpineBookingApplication.class, args);
    }
}
