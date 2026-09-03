buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.h2)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management.plugin)
    alias(libs.plugins.flyway.plugin)
    alias(libs.plugins.jooq.codegen.gradle)
    alias(libs.plugins.spotless)
}

val jvmTargetVersion =
    JavaLanguageVersion.of(
        libs.versions.jvmTarget
            .get()
            .toInt(),
    )

springBoot {
    mainClass.set("com.dominikgaller.alpinebooking.bootstrap.AlpineBookingApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.h2)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.resttestclient)

    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)

    jooqCodegen(libs.h2)
    jooqCodegen(libs.jooq.meta)
    runtimeOnly(libs.jooq.codegen)
}

// Toolchain is the single authoritative JVM version declaration
// (adr/0009-kotlin-migration.adr.md, superseding ADR 0006's Java 25 baseline).
// The vendor is pinned so the build is reproducible: without it, Gradle's
// auto-detection matches any locally installed JDK reporting the target language
// version, including early-access builds. foojay-resolver (see settings.gradle.kts)
// provisions Temurin if it is absent.
kotlin {
    jvmToolchain {
        languageVersion.set(jvmTargetVersion)
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

// H2 file-based database for jOOQ code generation – persists across Flyway and jOOQ tasks
// even if they run in separate JVM processes. Deleted by `./gradlew clean`.
val codegenDbPath = "${layout.buildDirectory.get().asFile.absolutePath}/jooq-codegen-db/codegen"
val codegenDbUrl = "jdbc:h2:file:$codegenDbPath"
val codegenDbUser = "sa"
val codegenDbPassword = ""

flyway {
    url = codegenDbUrl
    user = codegenDbUser
    password = codegenDbPassword
    cleanDisabled = false
    locations = arrayOf("filesystem:$projectDir/src/main/resources/db/migration")
}

jooq {
    configuration {
        jdbc {
            driver = "org.h2.Driver"
            url = codegenDbUrl
            user = codegenDbUser
            password = codegenDbPassword
        }
        generator {
            database {
                name = "org.jooq.meta.h2.H2Database"
                includes = ".*"
                excludes = "flyway_schema_history"
                inputSchema = "PUBLIC"
            }
            generate {}
            target {
                packageName = "com.dominikgaller.alpinebooking.jooq"
                directory = "build/generated-src/jooq/main"
            }
        }
    }
}

// Generated jOOQ sources stay Java (adr/0009-kotlin-migration.adr.md); the Kotlin
// compiler reads them directly from the shared main source set for cross-compilation,
// so both compile tasks need the generated sources to exist first.
tasks.named("compileKotlin") {
    dependsOn(tasks.named("jooqCodegen"))
}

tasks.named("compileJava") {
    dependsOn(tasks.named("jooqCodegen"))
}

tasks.named("jooqCodegen") {
    dependsOn(tasks.named("flywayMigrate"))
    inputs.files(fileTree("src/main/resources/db/migration"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

// Spring Boot 4.0.1 BOM pins jOOQ to 3.19.x; force runtime to 3.20.x to match codegen.
val jooqVersion = libs.versions.jooq.get()
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jooq") {
            useVersion(jooqVersion)
        }
    }
}
// Formatting gate (technical.spec.md, Build & Quality Gates; adr/0009-kotlin-migration.adr.md).
// ktlint via Spotless, replacing the Java-era hygiene-only java{} block — see ADR 0009 for
// why ktlint rather than Detekt. Only Kotlin sources are targeted; the generated jOOQ Java
// sources are neither hand-written nor ours to format.
spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
