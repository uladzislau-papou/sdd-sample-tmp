package com.dominikgaller.alpinebooking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mechanical enforcement of the bounded-context registry in
 * {@code documentation/architecture.definition.md} section 11.
 *
 * <p>The registry exists so that context boundaries are a checkable fact rather than a
 * matter of opinion. These tests make adding a fifth top-level package fail the build.
 *
 * <p>SDD: See {@code documentation/adr/0007-archunit-boundary-enforcement.adr.md} and
 *          {@code documentation/adr/0005-bounded-context-identity-boundaries.adr.md}.
 */
class ContextRegistryTest {

    private static final String ROOT = "com.dominikgaller.alpinebooking";

    /** The section 11 table. Adding a row here requires an ADR — and the ADR lands first. */
    private static final Set<String> REGISTERED_TOP_LEVEL_PACKAGES =
            Set.of("booking", "guide", "shared", "bootstrap");

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/jooq/"))
                .importPackages(ROOT);
    }

    /**
     * Guard against a silently empty import.
     *
     * <p>ArchUnit reads nothing at all if its bundled ASM does not understand the class
     * file version — which is exactly what happened on ArchUnit 1.3.0 and 1.4.0 against
     * the Java 25 baseline (ADR 0006): zero classes, no error. Every rule below would
     * then pass vacuously if ArchUnit lacked its own "failed to check any classes" guard.
     * This test makes the failure unmistakable rather than relying on that.
     */
    @Test
    @DisplayName("Guard: the importer actually found production classes")
    void importer_findsProductionClasses() {
        assertThat(production.size())
                .as("ArchUnit imported no classes — most likely its ASM cannot read the "
                        + "current class file version. See ADR 0006 and ADR 0007.")
                .isGreaterThan(50);
    }

    @Test
    @DisplayName("Section 11: top-level packages are exactly the registered ones")
    void topLevelPackages_areExactlyTheRegisteredSet() {
        final Set<String> onDisk = production.stream()
                .map(c -> c.getPackageName())
                .filter(p -> p.startsWith(ROOT + "."))
                .map(p -> p.substring(ROOT.length() + 1))
                .map(p -> p.contains(".") ? p.substring(0, p.indexOf('.')) : p)
                .collect(Collectors.toSet());

        assertThat(onDisk)
                .as("A top-level package not registered in architecture.definition.md "
                        + "section 11 is drift, however sensible it looks. A new bounded "
                        + "context is an ADR trigger (sdd.playbook.md section 6 item 9); "
                        + "adr/0003 is the precedent.")
                .isEqualTo(REGISTERED_TOP_LEVEL_PACKAGES);
    }

    @Test
    @DisplayName("Section 11 rule 3: booking does not import guide")
    void booking_doesNotImportGuide() {
        noClasses()
                .that().resideInAPackage(ROOT + ".booking..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".guide..")
                .because("contexts communicate through shared.domain.event or an explicit "
                        + "outport, never by importing each other (ADR 0002, ADR 0003).")
                .check(production);
    }

    @Test
    @DisplayName("Section 11 rule 3: guide does not import booking")
    void guide_doesNotImportBooking() {
        noClasses()
                .that().resideInAPackage(ROOT + ".guide..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".booking..")
                .because("contexts communicate through shared.domain.event or an explicit "
                        + "outport, never by importing each other (ADR 0002, ADR 0003).")
                .check(production);
    }

    @Test
    @DisplayName("Section 11 rule 4: shared depends on no bounded context")
    void shared_dependsOnNoBoundedContext() {
        noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(ROOT + ".booking..", ROOT + ".guide..")
                .because("architecture.definition.md section 9: any context may depend on "
                        + "shared; shared must not depend on any context.")
                .check(production);
    }

    @Test
    @DisplayName("Section 9: adapters for shared.outport live in shared.outbound")
    void sharedPortAdapters_liveInSharedOutbound() {
        noClasses()
                .that().resideInAnyPackage(ROOT + ".booking.outbound..", ROOT + ".guide.outbound..")
                .should().implement(ROOT + ".shared.outport.ClockPort")
                .orShould().implement(ROOT + ".shared.outport.DomainEventPublisher")
                .because("architecture.definition.md section 9: an implementation of a "
                        + "shared port belongs to no context, so it lives in "
                        + "shared.outbound and is wired by bootstrap.SharedConfig. Putting "
                        + "it in one context forces the other to obtain it from that "
                        + "context's configuration.")
                .check(production);
    }

    /**
     * ADR 0005: an identity owned by one context must not be imported by another. Foreign
     * identities cross the boundary as plain {@code String}.
     */
    @Test
    @DisplayName("ADR 0005: no context imports another context's identity type")
    void identities_doNotCrossContextBoundaries() {
        noClasses()
                .that().resideInAPackage(ROOT + ".booking..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(ROOT + ".guide.core.domain.guidetour.GuideTourId")
                .because("ADR 0005: guideTourId crosses into booking as a plain String, so "
                        + "guide can change its identity representation without recompiling "
                        + "booking.")
                .check(production);
    }
}
