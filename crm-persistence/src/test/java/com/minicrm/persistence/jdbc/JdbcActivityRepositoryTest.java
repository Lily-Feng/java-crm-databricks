package com.minicrm.persistence.jdbc;

import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.activity.Call;
import com.minicrm.domain.activity.Email;
import com.minicrm.domain.activity.Meeting;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import com.minicrm.persistence.jdbc.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActivityRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JdbcCustomerRepository customers = new JdbcCustomerRepository(PostgresTestSupport.dataSource(), 5);
    private final JdbcActivityRepository repository = new JdbcActivityRepository(PostgresTestSupport.dataSource(), 5);
    private CustomerId customerId;

    @BeforeEach
    void resetSchema() {
        PostgresTestSupport.truncateAll();
        customerId = CustomerId.newId();
        customers.save(Customer.create(customerId, "Acme", "Retail", new EmailAddress("a@acme.test"), NOW));
    }

    @Test
    void callRoundTrips() {
        var call = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 15);

        repository.save(call, "key-1", "hash-1");
        var found = repository.findByCustomerId(customerId);

        assertThat(found).containsExactly(call);
    }

    @Test
    void emailRoundTrips() {
        var email = new Email(ActivityId.newId(), customerId, "Proposal", NOW, "alice", "See attached.");

        repository.save(email, "key-1", "hash-1");

        assertThat(repository.findByCustomerId(customerId)).containsExactly(email);
    }

    @Test
    void meetingRoundTripsIncludingTheAttendeesArray() {
        var meeting = new Meeting(ActivityId.newId(), customerId, "Kickoff", NOW, "alice", List.of("alice", "bob"));

        repository.save(meeting, "key-1", "hash-1");

        assertThat(repository.findByCustomerId(customerId)).containsExactly(meeting);
    }

    @Test
    void uniqueConstraintIsEnforcedByTheDatabaseNotJustApplicationLogic() {
        var first = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);
        var second = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);

        var firstWinner = repository.save(first, "key-1", "hash-1");
        var secondWinner = repository.save(second, "key-1", "hash-1");

        assertThat(secondWinner.activity()).isEqualTo(firstWinner.activity());
        assertThat(repository.findByCustomerId(customerId)).hasSize(1);
    }

    @Test
    void concurrentDuplicateCallsAgainstRealPostgresProduceExactlyOneStoredActivity() throws Exception {
        var barrier = new CyclicBarrier(8);
        List<Callable<Activity>> attempts = IntStream.range(0, 8)
                .<Callable<Activity>>mapToObj(i -> () -> {
                    var candidate = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);
                    barrier.await();
                    return repository.save(candidate, "key-1", "hash-1").activity();
                })
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var results = executor.invokeAll(attempts);
            var winners = results.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).distinct().toList();

            assertThat(winners).hasSize(1);
            assertThat(repository.findByCustomerId(customerId)).hasSize(1);
        } finally {
            executor.shutdown();
        }
    }
}
