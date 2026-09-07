plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management.plugin)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "com.example"

val javaVersion = libs.versions.java.get().toInt()

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.graphql)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.graphql.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.data.jpa.test)
    testImplementation(libs.spring.boot.jdbc.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
}

// The toolchain is the single authoritative Java version declaration (ADR 0006).
// The vendor is deliberately not pinned: the baseline is an LTS release, so the
// early-access ambiguity that justified pinning a vendor for Java 25 no longer
// applies, and pinning would force a second JDK download on machines that already
// have a matching one.
kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors.set(true)
    }
}

// `test.definition.md` already separates fast tests from adapter integration tests by
// name. The build now separates them too, because one Gradle task that needs Docker gives
// a developer without Docker no fast feedback at all — and a gate that cannot be run
// locally is a gate that gets discovered in CI.
//
// `test`            — domain, use case and slice tests. No Docker, seconds.
// `integrationTest` — every `*IT`. Starts PostgreSQL through Testcontainers.
// `check`           — depends on both, so nothing is quietly skipped in CI.
tasks.named<Test>("test") {
    useJUnitPlatform()
    filter { excludeTestsMatching("*IT") }
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs adapter integration tests (*IT) against real infrastructure. Requires Docker."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*IT") }
    shouldRunAfter(tasks.named("test"))
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

// Formatting gate (technical.spec.md, Build & Quality Gates).
// Unlike the Java baseline this replaces, ktlint *does* apply a format: Kotlin has one
// community style and no hand-written house style to preserve, so there is nothing to
// bury. `spotlessApply` is the fix; `spotlessCheck` is the gate.
spotless {
    val excluded = listOf("**/build/**", "**/.gradle/**")

    kotlin {
        target("src/**/*.kt")
        targetExclude(excluded)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.kts")
        targetExclude(excluded)
        ktlint(libs.versions.ktlint.get())
    }
}

// Static analysis gate. Detekt catches the defect classes ArchUnit cannot see —
// complexity, swallowed exceptions, platform-type leaks — and ArchUnit catches the
// ones detekt cannot: layering and dependency direction. Neither replaces the other.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
}

// Both gates are wired explicitly rather than relying on either plugin's defaults:
// `./gradlew build` must fail on a style or static-analysis violation, and a plugin
// that silently stops contributing to `check` is a gate that silently disappears.
tasks.named("check") {
    dependsOn(integrationTest)
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(tasks.withType<dev.detekt.gradle.Detekt>())
}
