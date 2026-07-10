package com.musicapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {
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

        final Integer listenerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listener", Integer.class);
        final Integer songCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM song", Integer.class);
        final Integer playlistCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM playlist", Integer.class);

        assertThat(migrationCount).isEqualTo(2);
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
                """, "bad_email_user", "not-an-email", "hash", "Female", "1999-01-01", "United States"))
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

    private static DataSource dataSource () {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
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
}
