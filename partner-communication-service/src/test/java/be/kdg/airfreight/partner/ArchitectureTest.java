package be.kdg.airfreight.partner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private final JavaClasses classes =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("be.kdg.airfreight.partner");

    @Test
    void domainStaysIndependentOfFrameworksAndOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "..api..",
                        "..application..",
                        "..infrastructure..")
                // Remove when domain implementation classes exist.
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void layersDoNotContainDependencyCycles() {
        slices().matching("be.kdg.airfreight.partner.(*)..")
                .should()
                .beFreeOfCycles()
                // Remove when layer implementation classes exist.
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void serviceDoesNotImportAnotherServicesImplementation() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "be.kdg.airfreight.flight..",
                        "be.kdg.airfreight.cargo..",
                        "be.kdg.airfreight.formatter..")
                .check(classes);
    }
}
