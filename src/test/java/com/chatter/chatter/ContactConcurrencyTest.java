package com.chatter.chatter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chatter.chatter.user.dto.RegisterRequest;
import com.chatter.chatter.user.repository.ContactRequestRepository;
import com.chatter.chatter.user.service.ContactService;
import com.chatter.chatter.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The races that the unique {@code pair_key} exists to settle.
 *
 * <p>Plain JUnit rather than Cucumber: these need several threads hitting the
 * same instant, which a sequential scenario cannot express.
 *
 * <p>Runs on H2, which enforces UNIQUE the same way Postgres does but reports
 * violations differently — so this doubles as the check that Spring's exception
 * translation really does surface a {@code DataIntegrityViolationException} on
 * H2, which the whole retry path depends on.
 */
@SpringBootTest(classes = ChatterApplication.class)
class ContactConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private ContactService contactService;

    @Autowired
    private UserService userService;

    @Autowired
    private ContactRequestRepository requestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM app_user.blocks");
        jdbcTemplate.execute("DELETE FROM app_user.contact_requests");
        jdbcTemplate.execute("DELETE FROM app_user.contacts");
        jdbcTemplate.execute("DELETE FROM chat.messages");
        jdbcTemplate.execute("DELETE FROM chat.chat_participants");
        jdbcTemplate.execute("DELETE FROM chat.chat_counters");
        jdbcTemplate.execute("DELETE FROM chat.chats");
        jdbcTemplate.execute("DELETE FROM app_user.user_profiles");
        jdbcTemplate.execute("DELETE FROM app_user.push_subscriptions");
        jdbcTemplate.execute("DELETE FROM app_user.users");

        alice = userService.register(new RegisterRequest("alice", null, "password123")).getId();
        bob = userService.register(new RegisterRequest("bob", null, "password123")).getId();
    }

    @Test
    void concurrentRequestsInTheSameDirectionLeaveExactlyOneRow() throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runTogether(THREADS, () -> {
            try {
                contactService.sendRequest(alice, bob);
                accepted.incrementAndGet();
            } catch (RuntimeException e) {
                rejected.incrementAndGet();
            }
            return null;
        });

        assertThat(requestRepository.findAll()).as("exactly one pending request survives").hasSize(1);
        assertThat(accepted.get()).as("exactly one caller is told it created the request").isEqualTo(1);
        assertThat(rejected.get()).as("every other caller is refused, not silently duplicated")
                .isEqualTo(THREADS - 1);
        // Still only a request: nobody is a contact until bob accepts.
        assertThat(contactService.areConnected(alice, bob)).isFalse();
    }

    @Test
    void requestsCrossingInOppositeDirectionsBecomeOneFriendship() throws Exception {
        // Half the threads send alice->bob, half send bob->alice, all at once.
        runTogether(THREADS, new Callable<Void>() {
            private final AtomicInteger seat = new AtomicInteger();

            @Override
            public Void call() {
                boolean fromAlice = seat.getAndIncrement() % 2 == 0;
                try {
                    contactService.sendRequest(fromAlice ? alice : bob, fromAlice ? bob : alice);
                } catch (RuntimeException ignored) {
                    // Losing the race is expected; what matters is the end state.
                }
                return null;
            }
        });

        assertThat(contactService.areConnected(alice, bob))
                .as("two people asking each other at once end up connected").isTrue();
        assertThat(requestRepository.findAll())
                .as("no request is left dangling once the friendship exists").isEmpty();
        assertThat(contactService.listContacts(alice)).hasSize(1);
        assertThat(contactService.listContacts(bob)).hasSize(1);
    }

    /** Releases every thread from a barrier so the calls genuinely overlap. */
    private void runTogether(int threads, Callable<Void> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return work.call();
                }));
            }
            for (Future<Void> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
