package com.example.contractmanagement.architecture

import com.example.contractmanagement.ContractManagementApplication
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption

/**
 * The one place the architecture tests learn where the code lives.
 *
 * [ROOT] is **derived** from the package [ContractManagementApplication] actually sits in, not written
 * as a string. Three tests previously repeated the same literal, so renaming the package
 * left three stale copies that still compiled and still passed — checking an empty set of
 * classes. Deriving it means a rename cannot silently disarm the gates.
 *
 * That is also why the entry point lives in the root package rather than in `bootstrap`
 * (see `ContractManagementApplication`): it makes the package root a fact about the code rather than
 * a convention someone maintains.
 *
 * SDD: see `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
object ArchitectureRoot {
    val ROOT: String = ContractManagementApplication::class.java.packageName

    /**
     * Production classes only.
     *
     * Tests are excluded deliberately. Test-only support packages — `architecture`,
     * `support` — are not bounded contexts, and including them would make
     * `ContextRegistryTest`'s "top-level packages are exactly the registered ones" fail on
     * the very scaffolding that enforces it.
     */
    fun productionClasses(): JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)
}
