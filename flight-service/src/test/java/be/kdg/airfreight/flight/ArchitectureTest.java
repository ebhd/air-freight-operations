package be.kdg.airfreight.flight;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private final JavaClasses classes =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("be.kdg.airfreight.flight");

    @Test
    void serviceDoesNotImportAnotherServicesImplementation() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("be.kdg.airfreight.cargo..", "be.kdg.airfreight.partner..")
                .check(classes);
    }
}
