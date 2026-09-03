package com.dominikgaller.alpinebooking.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Composition root. Scans the shared root package so every bounded context is
 * discovered automatically.
 *
 * SDD: architecture.definition.md § 4.9.
 */
@SpringBootApplication(scanBasePackages = ["com.dominikgaller.alpinebooking"])
class AlpineBookingApplication

fun main(args: Array<String>) {
    runApplication<AlpineBookingApplication>(*args)
}
