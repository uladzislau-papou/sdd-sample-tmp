package com.example.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot entry point.
 *
 * Deliberately placed in the root package rather than in `bootstrap`. Two rules depend on
 * that position and both become self-maintaining because of it:
 *
 *  - Spring's component scan defaults to this class's own package, so every bounded
 *    context is discovered without a `scanBasePackages` literal to keep in sync.
 *  - The architecture tests derive their package root from this class, so renaming the
 *    package cannot leave a stale string behind (see the `architecture` test helper).
 *
 * Wiring itself still lives in `bootstrap`, which belongs to no bounded context.
 *
 * SDD: see `documentation/architecture.definition.md`, section 4.9.
 */
@SpringBootApplication
class ServiceApplication

fun main(args: Array<String>) {
    runApplication<ServiceApplication>(*args)
}
