package com.musicapp;

import com.musicapp.catalog.CatalogService;
import com.musicapp.catalog.DiscoveryService;
import com.musicapp.catalog.SongRepository;
import com.musicapp.catalog.dto.SearchSuggestionResponse;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerActionService;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.recommendation.RecommendationService;
import com.musicapp.recommendation.dto.RecommendationResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {
    private static final int MAX_RECOMMENDATION_PAGE_SIZE = 25;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("music_app_test")
            .withUsername("music_app")
            .withPassword("music_app");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateDatabase () {
        final DataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void flywayRunsSchemaAndSeedMigrations () {
        final Integer migrationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = TRUE
                """, Integer.class);

        final Integer listenerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listener WHERE listener_id BETWEEN 1 AND 15",
                Integer.class
        );
        final Integer songCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM song WHERE song_id BETWEEN 1 AND 32",
                Integer.class
        );
        final Integer playlistCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM playlist WHERE playlist_id BETWEEN 1 AND 8",
                Integer.class
        );

        assertThat(migrationCount).isGreaterThanOrEqualTo(6);
        assertThat(listenerCount).isEqualTo(15);
        assertThat(songCount).isEqualTo(32);
        assertThat(playlistCount).isEqualTo(8);
    }

    @Test
    void enumCastsWorkForListenerAndPlaylistActions () {
        jdbcTemplate.update("""
                INSERT INTO listener_song_activity (listener_id, song_id, attitude)
                VALUES (?, ?, ?::attitude)
                ON CONFLICT (listener_id, song_id) DO UPDATE SET attitude = EXCLUDED.attitude
                """, 11L, 30L, "not_interested");

        jdbcTemplate.update("""
                INSERT INTO playlist (playlist_name, type, creator_id)
                VALUES (?, ?::playlist_type, ?)
                """, "Enum Cast Playlist", "shared", 11L);

        final String attitude = jdbcTemplate.queryForObject("""
                SELECT attitude::text
                FROM listener_song_activity
                WHERE listener_id = ?
                  AND song_id = ?
                """, String.class, 11L, 30L);

        final String playlistType = jdbcTemplate.queryForObject("""
                SELECT type::text
                FROM playlist
                WHERE playlist_name = ?
                """, String.class, "Enum Cast Playlist");

        assertThat(attitude).isEqualTo("not_interested");
        assertThat(playlistType).isEqualTo("shared");
    }

    @Test
    void favoriteSongsStayOrderedByLikeTimeAfterStreaming () {
        final TestListener listener = createListener("favorite_order");
        jdbcTemplate.update("""
                INSERT INTO listener_song_activity (
                    listener_id,
                    song_id,
                    attitude,
                    attitude_updated_at,
                    updated_at
                )
                VALUES
                    (?, 1, 'like'::attitude, TIMESTAMPTZ '2026-01-01 00:00:00Z', TIMESTAMPTZ '2026-01-01 00:00:00Z'),
                    (?, 2, 'like'::attitude, TIMESTAMPTZ '2026-01-02 00:00:00Z', TIMESTAMPTZ '2026-01-02 00:00:00Z')
                """, listener.id(), listener.id());

        final SongResponse first = songResponse(2L, "Most recently liked");
        final SongResponse second = songResponse(1L, "Most recently streamed");
        final SongRepository songRepository = mock(SongRepository.class);
        final CatalogService catalogService = mock(CatalogService.class);
        when(songRepository.existsById(1L)).thenReturn(true);
        when(catalogService.getSongsByIds(List.of(2L, 1L))).thenReturn(Map.of(1L, second, 2L, first));

        final ListenerActionService listenerActionService = new ListenerActionService(
                listenerRepository(listener.id(), listener.username()),
                songRepository,
                null,
                jdbcTemplate,
                catalogService
        );

        listenerActionService.streamSong(listener.username(), 1L, false);

        assertThat(listenerActionService.getFavoriteSongs(listener.username())).containsExactly(first, second);
    }

    @Test
    void discoverySuggestionsRunAgainstTheMigratedPostgresSchema () {
        final DiscoveryService discoveryService = new DiscoveryService(
                listenerRepository(1L),
                new EmptyCatalogService(),
                jdbcTemplate
        );

        final List<SearchSuggestionResponse> suggestions = discoveryService.getSearchSuggestions("aurora", 8);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst().type()).isEqualTo("performer");
        assertThat(suggestions.getFirst().title()).isEqualTo("Aurora Sky");
        assertThat(suggestions)
                .extracting(SearchSuggestionResponse::type)
                .contains("album", "song");
    }

    @Test
    void playlistSongVoteJoinReturnsSeededVoteTotals () {
        final PlaylistVoteTotal voteTotal = jdbcTemplate.queryForObject("""
                SELECT
                    ps.playlist_id,
                    ps.song_id,
                    s.title,
                    COUNT(v.listener_id)::int AS vote_count
                FROM playlist_song ps
                JOIN song s ON s.song_id = ps.song_id
                LEFT JOIN playlist_song_vote v
                    ON v.playlist_id = ps.playlist_id
                   AND v.song_id = ps.song_id
                WHERE ps.playlist_id = ?
                  AND ps.song_id = ?
                GROUP BY ps.playlist_id, ps.song_id, s.title
                """, (rs, rowNum) -> new PlaylistVoteTotal(
                rs.getLong("playlist_id"),
                rs.getLong("song_id"),
                rs.getString("title"),
                rs.getInt("vote_count")
        ), 8L, 9L);

        assertThat(voteTotal).isEqualTo(new PlaylistVoteTotal(8L, 9L, "Moon Dance", 2));
    }

    @Test
    void databaseConstraintsRejectInvalidRows () {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO listener (
                    username,
                    email_address,
                    password_hash,
                    gender,
                    date_of_birth,
                    country_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """, "bad_email_user", "not-an-email", "hash", "Female", LocalDate.of(1999, 1, 1), "United States"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO listener_recommendation (listener_id, song_id, recommendation_score)
                VALUES (?, ?, ?)
                """, 11L, 30L, new BigDecimal("-1.0000")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO playlist_song_vote (playlist_id, song_id, listener_id, vote)
                VALUES (?, ?, ?, ?)
                """, 8L, 9L, 11L, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recommendationsAreReturnedInScoreOrder () {
        final List<RecommendedSong> recommendations = jdbcTemplate.query("""
                SELECT
                    lr.song_id,
                    s.title,
                    lr.recommendation_score
                FROM listener_recommendation lr
                JOIN song s ON s.song_id = lr.song_id
                WHERE lr.listener_id = ?
                ORDER BY lr.recommendation_score DESC, lr.generated_at DESC, s.title
                """, (rs, rowNum) -> new RecommendedSong(
                rs.getLong("song_id"),
                rs.getString("title"),
                rs.getBigDecimal("recommendation_score")
        ), 1L);

        assertThat(recommendations).hasSize(5);
        assertThat(recommendations)
                .extracting(RecommendedSong::score)
                .containsExactly(
                        new BigDecimal("162.5000"),
                        new BigDecimal("162.5000"),
                        new BigDecimal("127.5000"),
                        new BigDecimal("112.5000"),
                        new BigDecimal("112.5000")
                );
        assertThat(recommendations.getFirst().score()).isGreaterThan(recommendations.get(2).score());
        assertThat(recommendations).extracting(RecommendedSong::songId).contains(3L, 4L, 10L, 17L, 18L);
    }

    @Test
    void rebuildRecommendationsUsesMigratedPostgresSchema () {
        final RecommendationService recommendationService = new RecommendationService(listenerRepository(2L), jdbcTemplate);
        final List<RecommendationResponse> recommendations = recommendationService.rebuildRecommendations("rockfan88", 5);

        assertThat(recommendations).hasSize(5);
        assertThat(recommendations).extracting(RecommendationResponse::score).isSortedAccordingTo((left, right) -> right.compareTo(left));
        assertThat(recommendations).extracting(recommendation -> recommendation.song().songId()).doesNotContain(5L, 6L, 11L, 19L, 21L, 28L, 29L);
    }

    @Test
    void likesAndFollowsIncreaseRecommendationScore () {
        final TestListener listener = createListener("scoring_like_follow");
        jdbcTemplate.update("""
                INSERT INTO listener_genre_priority (listener_id, genre_name, priority_score)
                VALUES (?, ?, ?)
                """, listener.id(), "Pop", new BigDecimal("10.0000"));

        final RecommendationService recommendationService = recommendationService(listener);
        final BigDecimal baseScore = scoreFor(recommendationService.rebuildRecommendations(listener.username(), MAX_RECOMMENDATION_PAGE_SIZE), 3L);

        jdbcTemplate.update("""
                INSERT INTO listener_following_performer (listener_id, performer_id)
                VALUES (?, ?)
                """, listener.id(), 1L);

        final BigDecimal followedScore = scoreFor(recommendationService.rebuildRecommendations(listener.username(), MAX_RECOMMENDATION_PAGE_SIZE), 3L);

        jdbcTemplate.update("""
                INSERT INTO listener_song_activity (listener_id, song_id, stream_count, skip_count, attitude)
                VALUES (?, ?, 0, 0, ?::attitude)
                """, listener.id(), 1L, "like");

        final BigDecimal likedScore = scoreFor(recommendationService.rebuildRecommendations(listener.username(), MAX_RECOMMENDATION_PAGE_SIZE), 3L);

        assertThat(followedScore).isGreaterThan(baseScore);
        assertThat(likedScore).isGreaterThan(followedScore);
    }

    @Test
    void dislikesSuppressSimilarRecommendations () {
        final TestListener listener = createListener("scoring_dislike");
        jdbcTemplate.update("""
                INSERT INTO listener_genre_priority (listener_id, genre_name, priority_score)
                VALUES (?, ?, ?)
                """, listener.id(), "Rock", new BigDecimal("10.0000"));
        jdbcTemplate.update("""
                INSERT INTO listener_song_activity (listener_id, song_id, stream_count, skip_count, attitude)
                VALUES (?, ?, 0, 0, ?::attitude)
                """, listener.id(), 1L, "dislike");

        final List<RecommendationResponse> recommendations = recommendationService(listener).rebuildRecommendations(listener.username(), MAX_RECOMMENDATION_PAGE_SIZE);

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations).extracting(recommendation -> recommendation.song().songId()).doesNotContain(3L, 4L);
    }

    @Test
    void genrePriorityAffectsRecommendationRanking () {
        final TestListener listener = createListener("scoring_genre_priority");
        jdbcTemplate.update("""
                INSERT INTO listener_genre_priority (listener_id, genre_name, priority_score)
                VALUES (?, ?, ?),
                       (?, ?, ?)
                """, listener.id(), "Rock", new BigDecimal("100.0000"), listener.id(), "Pop", new BigDecimal("10.0000"));

        final List<RecommendationResponse> recommendations = recommendationService(listener).rebuildRecommendations(listener.username(), MAX_RECOMMENDATION_PAGE_SIZE);

        assertThat(scoreFor(recommendations, 5L)).isGreaterThan(scoreFor(recommendations, 3L));
    }

    @Test
    void blockedSongsAndPerformersNeverAppearInRecommendations () {
        final TestListener listener = createListener("scoring_blocks");
        jdbcTemplate.update("""
                INSERT INTO listener_genre_priority (listener_id, genre_name, priority_score)
                VALUES (?, ?, ?)
                """, listener.id(), "Rock", new BigDecimal("100.0000"));
        jdbcTemplate.update("""
                INSERT INTO blocked_song (listener_id, song_id)
                VALUES (?, ?)
                """, listener.id(), 15L);
        jdbcTemplate.update("""
                INSERT INTO blocked_performer (listener_id, performer_id)
                VALUES (?, ?)
                """, listener.id(), 2L);

        final List<RecommendationResponse> recommendations = recommendationService(listener).rebuildRecommendations(listener.username(), MAX_RECOMMENDATION_PAGE_SIZE);

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations)
                .extracting(recommendation -> recommendation.song().songId())
                .doesNotContain(5L, 6L, 7L, 8L, 15L);
    }

    private static DataSource dataSource () {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private TestListener createListener (String usernamePrefix) {
        final String username = usernamePrefix + "_" + System.nanoTime();
        final Long listenerId = jdbcTemplate.queryForObject("""
                INSERT INTO listener (
                    username,
                    email_address,
                    password_hash,
                    gender,
                    date_of_birth,
                    country_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING listener_id
                """, Long.class, username, username + "@example.com", "password-hash", "Female", LocalDate.of(1999, 1, 1), "United States");

        return new TestListener(listenerId, username);
    }

    private RecommendationService recommendationService (TestListener listener) {
        return new RecommendationService(listenerRepository(listener.id(), listener.username()), jdbcTemplate);
    }

    private BigDecimal scoreFor (List<RecommendationResponse> recommendations, Long songId) {
        return recommendations.stream()
                .filter(recommendation -> recommendation.song().songId().equals(songId))
                .findFirst()
                .map(RecommendationResponse::score)
                .orElseThrow();
    }

    private SongResponse songResponse (Long songId, String title) {
        return new SongResponse(songId, title, null, null, null, null, null, null, List.of());
    }

    private ListenerRepository listenerRepository (Long listenerId) {
        return listenerRepository(listenerId, "rockfan88");
    }

    private ListenerRepository listenerRepository (Long listenerId, String username) {
        final Listener listener = Listener.register(
                username,
                username + "@example.com",
                "password-hash",
                "Male",
                LocalDate.of(1992, 6, 10),
                "United States"
        );
        ReflectionTestUtils.setField(listener, "id", listenerId);

        return (ListenerRepository) Proxy.newProxyInstance(
                ListenerRepository.class.getClassLoader(),
                new Class<?>[]{ListenerRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUsername")) {
                        return Optional.of(listener);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private record TestListener(
            Long id,
            String username
    ) {
    }

    private record PlaylistVoteTotal(
            Long playlistId,
            Long songId,
            String title,
            int voteCount
    ) {
    }

    private record RecommendedSong(
            Long songId,
            String title,
            BigDecimal score
    ) {
    }

    private static class EmptyCatalogService extends CatalogService {
        EmptyCatalogService () {
            super(null, null, null, null, null);
        }
    }
}
