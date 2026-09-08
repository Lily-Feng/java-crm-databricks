package com.minicrm.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * §4: "Domain code has no Spring or JDBC dependencies." §1's required outcome that the
 * dependency direction (crm-persistence/crm-databricks/crm-api depend on crm-domain,
 * never the reverse) starts being enforced here, before those modules exist.
 */
class DomainHasNoFrameworkDependenciesTest {

    private final com.tngtech.archunit.core.domain.JavaClasses domainClasses =
            new ClassFileImporter().importPackages("com.minicrm.domain");

    @Test
    void domainDoesNotDependOnSpring() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .allowEmptyShould(true)
                .check(domainClasses);
    }

    @Test
    void domainDoesNotDependOnJdbc() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("java.sql..", "javax.sql..")
                .allowEmptyShould(true)
                .check(domainClasses);
    }
}
