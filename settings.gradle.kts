plugins {
    // Provisions the pinned toolchain (build.gradle.kts) when the machine lacks it,
    // so a clone builds without a manual JDK install. See
    // documentation/adr/0006-jvm-and-kotlin-baseline.adr.md.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "contract-management"
