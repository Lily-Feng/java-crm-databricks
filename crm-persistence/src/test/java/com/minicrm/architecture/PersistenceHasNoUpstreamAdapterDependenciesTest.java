package com.minicrm.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * §4: crm-persistence depends only on crm-application/crm-domain. crm-databricks depends
 * on crm-persistence (to reuse its Postgres repository classes for Lakebase), never the
 * reverse, and crm-api/crm-mcp are composition roots that depend on crm-persistence, not
 * the other way around. No Spring dependency yet either — that arrives at Milestone 5,
 * in crm-api, not here.
 */
class PersistenceHasNoUpstreamAdapterDependenciesTest {

    private final JavaClasses persistenceClasses =
            new ClassFileImporter().importPackages("com.minicrm.persistence");

    @Test
    void persistenceDoesNotDependOnSpring() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .allowEmptyShould(true)
                .check(persistenceClasses);
    }

    @Test
    void persistenceDoesNotDependOnDownstreamAdapterModules() {
        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.minicrm.databricks..", "com.minicrm.api..", "com.minicrm.mcp..")
                .allowEmptyShould(true)
                .check(persistenceClasses);
    }
}
