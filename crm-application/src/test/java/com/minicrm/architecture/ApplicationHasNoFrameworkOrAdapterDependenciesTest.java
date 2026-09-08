package com.minicrm.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * §4: "Application code never imports adapters." crm-persistence/crm-databricks/crm-api
 * don't exist yet, but this guard is added now, before there is anything to violate it,
 * so the first adapter that reaches back into com.minicrm.application fails the build
 * immediately rather than being caught in review.
 */
class ApplicationHasNoFrameworkOrAdapterDependenciesTest {

    private final JavaClasses applicationClasses =
            new ClassFileImporter().importPackages("com.minicrm.application");

    @Test
    void applicationDoesNotDependOnSpring() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .allowEmptyShould(true)
                .check(applicationClasses);
    }

    @Test
    void applicationDoesNotDependOnJdbc() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("java.sql..", "javax.sql..")
                .allowEmptyShould(true)
                .check(applicationClasses);
    }

    @Test
    void applicationDoesNotDependOnAdapterModules() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.minicrm.persistence..", "com.minicrm.databricks..",
                        "com.minicrm.api..", "com.minicrm.mcp..")
                .allowEmptyShould(true)
                .check(applicationClasses);
    }
}
