plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"

    // Generates the no-arg constructors JPA requires. It applies only to classes
    // annotated @Entity / @Embeddable / @MappedSuperclass, all of which live in
    // outbound.persistence — the domain carries no JPA annotation and is therefore
    // untouched by this plugin (architecture.definition.md § 4.1).
    kotlin("plugin.jpa") version "2.3.0"

    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.0"
    id("dev.detekt") version "2.0.0-alpha.2"
    jacoco
}

group = "com.jobradleasing"
version = "0.0.1-SNAPSHOT"

// The toolchain is the single authoritative JVM version declaration
// (adr/0006-jvm-and-kotlin-baseline.adr.md). The vendor is pinned so the build is
// reproducible: without it Gradle's auto-detection matches any locally installed JDK
// reporting language version 17, so the selected JDK would depend on the machine.
// foojay-resolver (settings.gradle.kts) provisions Temurin if it is absent.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-graphql-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors = true
    }
}

spotless {
    val excludedPaths = listOf("**/.gradle/**", "**/build/**")

    kotlin {
        target("**/*.kt")
        targetExclude(excludedPaths)
        ktlint("1.5.0")
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude(excludedPaths)
        ktlint("1.5.0")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
}

// Quality gates are canonical in documentation/test.definition.md § 7; this wires the
// two that are Gradle's to enforce, so `./gradlew build` covers them.
tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
