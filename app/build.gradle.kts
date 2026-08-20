buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.h2)
    }
}

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management.plugin)
    alias(libs.plugins.flyway.plugin)
    alias(libs.plugins.jooq.codegen.gradle)
}

val javaVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())

springBoot {
    mainClass.set("com.dominikgaller.alpinebooking.bootstrap.AlpineBookingApplication")
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
    runtimeOnly(libs.h2)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.resttestclient)

    testImplementation(libs.assertj.core)

    jooqCodegen(libs.h2)
    jooqCodegen(libs.jooq.meta)
    runtimeOnly(libs.jooq.codegen)
}

// Toolchain is the single authoritative Java version declaration (ADR 0006).
// The vendor is pinned so the build is reproducible: without it, Gradle's
// auto-detection matches any locally installed JDK reporting language version 25 —
// including early-access builds — so the selected JDK would depend on the machine.
// foojay-resolver (see settings.gradle.kts) provisions Temurin if it is absent.
java {
    toolchain {
        languageVersion.set(javaVersion)
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
    locations = arrayOf("filesystem:${projectDir}/src/main/resources/db/migration")
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