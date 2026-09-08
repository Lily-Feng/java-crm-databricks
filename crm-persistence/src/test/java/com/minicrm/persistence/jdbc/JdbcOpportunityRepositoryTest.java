package com.minicrm.persistence.jdbc;

import com.minicrm.application.service.OpportunityNotFoundException;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.opportunity.StaleVersionException;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;
import com.minicrm.persistence.jdbc.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOpportunityRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JdbcCustomerRepository customers = new JdbcCustomerRepository(PostgresTestSupport.dataSource(), 5);
    private final JdbcOpportunityRepository repository = new JdbcOpportunityRepository(PostgresTestSupport.dataSource(), 5);
    private CustomerId customerId;

    @BeforeEach
    void resetSchema() {
        PostgresTestSupport.truncateAll();
        customerId = CustomerId.newId();
        customers.save(Customer.create(customerId, "Acme", "Retail", new EmailAddress("a@acme.test"), NOW));
    }

    private Opportunity opened() {
        return Opportunity.open(
                OpportunityId.newId(), customerId, "Acme renewal", Money.of("USD", 10_000_00),
                Probability.of(0.3), "alice", NOW);
    }

    @Test
    void insertThenFindRoundTrips() {
        var opportunity = opened();
        repository.save(opportunity);

        var found = repository.findById(opportunity.id()).orElseThrow();

        assertThat(found.name()).isEqualTo("Acme renewal");
        assertThat(found.stage()).isEqualTo(Stage.QUALIFIED);
        assertThat(found.amount()).isEqualTo(Money.of("USD", 10_000_00));
    }

    @Test
    void foreignKeyRejectsAnOpportunityForAnUnknownCustomer() {
        var orphan = Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), "Orphan", Money.of("USD", 100_00),
                Probability.of(0.2), "alice", NOW);

        assertThatThrownBy(() -> repository.save(orphan)).isInstanceOf(UncheckedSqlException.class);
    }

    @Test
    void checkConstraintRejectsAnInvalidStageWrittenDirectly() throws Exception {
        var opportunity = opened();
        repository.save(opportunity);

        try (var connection = PostgresTestSupport.dataSource().getConnection();
             var statement = connection.prepareStatement("UPDATE opportunities SET stage = ? WHERE opportunity_id = ?")) {
            statement.setString(1, "NOT_A_REAL_STAGE");
            statement.setObject(2, opportunity.id().value());
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void updateBasedOnAMissingOpportunityRaisesNotFound() {
        var neverSaved = opened();
        neverSaved.changeStage(Stage.PROPOSAL, 0, NOW);

        assertThatThrownBy(() -> repository.save(neverSaved)).isInstanceOf(OpportunityNotFoundException.class);
    }

    @Test
    void updateBasedOnAStaleVersionIsRejected() {
        var opportunity = opened();
        repository.save(opportunity);
        opportunity.changeStage(Stage.PROPOSAL, 0, NOW);
        repository.save(opportunity); // now version 1 in the database

        // A caller that loaded version 0 before the update above, and only now tries to
        // save its own version-1 result — exactly what the concurrent test below produces
        // under a real race, reproduced directly here for a single-threaded assertion.
        var stale = new Opportunity(
                opportunity.id(), customerId, "Acme renewal", Money.of("USD", 10_000_00),
                Stage.NEGOTIATION, Probability.of(0.3), "alice", "", 1, NOW, NOW);

        assertThatThrownBy(() -> repository.save(stale)).isInstanceOf(StaleVersionException.class);
    }

    @Test
    void concurrentSameVersionUpdatesAgainstRealPostgresProduceOneSuccessAndOneConflict() throws Exception {
        var opportunity = opened();
        repository.save(opportunity);

        var barrier = new CyclicBarrier(2);
        Callable<Opportunity> attempt = () -> {
            var copy = repository.findById(opportunity.id()).orElseThrow();
            copy.changeStage(Stage.PROPOSAL, 0, NOW);
            barrier.await();
            return repository.save(copy);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Opportunity>> futures = executor.invokeAll(List.of(attempt, attempt));

            int successes = 0;
            int conflicts = 0;
            for (Future<Opportunity> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(StaleVersionException.class);
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(repository.findById(opportunity.id()).orElseThrow().version()).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
