package com.musicapp.playlist;

import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.playlist.dto.GeneratedPlaylistResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GeneratedPlaylistConcurrencyIntegrationTest {
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("music_app_test")
            .withUsername("music_app")
            .withPassword("music_app");

    @DynamicPropertySource
    static void configurePostgres (DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private GeneratedPlaylistService generatedPlaylistService;

    @Autowired
    private GeneratedPlaylistRepository generatedPlaylistRepository;

    @Autowired
    private ListenerRepository listenerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentRequestsInsertOnceAndTheSecondRequestReloadsTheInsertedPlaylist () throws Exception {
        final Listener listener = listenerRepository.saveAndFlush(Listener.register(
                "concurrent_playlist_listener",
                "concurrent-playlist-listener@example.com",
                "password-hash",
                "Female",
                LocalDate.of(1995, 5, 15),
                "Armenia"
        ));
        final GeneratedPlaylistType playlistType = GeneratedPlaylistType.DAILY_REWIND;
        final long lockKey = (listener.getId() << Byte.SIZE) | (playlistType.getLockCode() & 0xFFL);

        assertThat(generatedPlaylistRepository.findByListenerIdAndPlaylistType(listener.getId(), playlistType)).isEmpty();

        final CountDownLatch blockerHasLock = new CountDownLatch(1);
        final CountDownLatch releaseBlocker = new CountDownLatch(1);
        final CountDownLatch requestsStarted = new CountDownLatch(2);
        final ExecutorService executor = Executors.newFixedThreadPool(3);
        final TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try {
            // Hold the same lock so both requests reach PostgreSQL before either can inspect the empty cache.
            final Future<?> blocker = executor.submit(() -> transactions.executeWithoutResult(status -> {
                acquireLock(lockKey);
                blockerHasLock.countDown();
                await(releaseBlocker);
            }));
            assertThat(blockerHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            final Future<GeneratedPlaylistResponse> firstRequest = submitGenerationRequest(
                    executor,
                    requestsStarted,
                    listener.getUsername(),
                    playlistType
            );
            final Future<GeneratedPlaylistResponse> secondRequest = submitGenerationRequest(
                    executor,
                    requestsStarted,
                    listener.getUsername(),
                    playlistType
            );
            assertThat(requestsStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitAdvisoryLockWaiters(2);

            releaseBlocker.countDown();

            final GeneratedPlaylistResponse firstResponse = firstRequest.get(10, TimeUnit.SECONDS);
            final GeneratedPlaylistResponse secondResponse = secondRequest.get(10, TimeUnit.SECONDS);
            blocker.get(10, TimeUnit.SECONDS);

            final List<GeneratedPlaylist> savedPlaylists = generatedPlaylistRepository.findAllByListenerId(listener.getId());
            assertThat(savedPlaylists).singleElement().satisfies(savedPlaylist -> {
                assertThat(savedPlaylist.getPlaylistType()).isEqualTo(playlistType);
                assertThat(firstResponse.generatedAt()).isCloseTo(savedPlaylist.getGeneratedAt(), within(1, ChronoUnit.MICROS));
                assertThat(secondResponse.generatedAt()).isCloseTo(savedPlaylist.getGeneratedAt(), within(1, ChronoUnit.MICROS));
                assertThat(firstResponse.expiresAt()).isCloseTo(savedPlaylist.getExpiresAt(), within(1, ChronoUnit.MICROS));
                assertThat(secondResponse.expiresAt()).isCloseTo(savedPlaylist.getExpiresAt(), within(1, ChronoUnit.MICROS));
            });
            assertThat(secondResponse.songs()).containsExactlyElementsOf(firstResponse.songs());
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
        }
    }

    private Future<GeneratedPlaylistResponse> submitGenerationRequest (
            ExecutorService executor,
            CountDownLatch requestsStarted,
            String username,
            GeneratedPlaylistType playlistType
    ) {
        return executor.submit(() -> {
            requestsStarted.countDown();
            return generatedPlaylistService.generatePlaylist(username, playlistType);
        });
    }

    private void acquireLock (long lockKey) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockKey);
                statement.execute();
            }

            return null;
        });
    }

    private void awaitAdvisoryLockWaiters (int expectedWaiterCount) throws InterruptedException {
        final long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            final Integer waiterCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_locks
                    WHERE locktype = 'advisory'
                      AND granted = FALSE
                    """, Integer.class);

            if (waiterCount != null && waiterCount == expectedWaiterCount) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError("Expected " + expectedWaiterCount + " requests to be waiting for the advisory lock");
    }

    private void await (CountDownLatch latch) {
        try {
            if (!latch.await(LOCK_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while holding the advisory lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while holding the advisory lock", exception);
        }
    }
}
