package com.cams.fileprocessing.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests enforcing the platform constitution's interface-first mandate.
 *
 * <p>These rules run on every build and cause a build failure if any violation is found.
 * They are the automated guardrails described in {@code constitution.md §2.2}.
 *
 * <h3>Rules Enforced</h3>
 * <ol>
 *   <li>Business logic ({@code business.*}) must not import infrastructure or cloud vendor packages</li>
 *   <li>Test sources must not import Mockito (Mockito is banned per §12.1)</li>
 * </ol>
 */
@Tag("architecture")
class PortAdapterArchitectureTest {

    static JavaClasses mainClasses;
    static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cams.fileprocessing");

        allClasses = new ClassFileImporter()
                .importPackages("com.cams.fileprocessing");
    }

    // -------------------------------------------------------------------------
    // Rule 1: Business layer isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Business layer must not depend on infrastructure packages")
    void businessLayer_mustNotDependOn_infrastructurePackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..business..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("Business layer must not import Google Cloud SDK classes")
    void businessLayer_mustNotDependOn_googleCloudSdk() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..business..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.google.cloud..", "com.google.api..");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("Business layer must not import AWS SDK classes")
    void businessLayer_mustNotDependOn_awsSdk() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..business..")
                .should().dependOnClassesThat()
                .resideInAPackage("software.amazon..");

        rule.check(mainClasses);
    }

    // -------------------------------------------------------------------------
    // Rule 2: No Mockito in new packages (business, infrastructure, interfaces)
    //
    // MIGRATION NOTE: The existing tests in features.upload.* still use Mockito
    // and are tracked for rewrite in the constitution backlog (§12.1).
    // This rule covers all new code. The scope will expand to the full codebase
    // once the upload tests are migrated to Testcontainers.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Business, infrastructure and interfaces packages must not use Mockito (banned per constitution §12.1)")
    void newPackages_mustNotUseMockito() {
        JavaClasses newPackages = new ClassFileImporter()
                .importPackages(
                        "com.cams.fileprocessing.business",
                        "com.cams.fileprocessing.infrastructure",
                        "com.cams.fileprocessing.interfaces"
                );

        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage("org.mockito..");

        rule.check(newPackages);
    }
}
