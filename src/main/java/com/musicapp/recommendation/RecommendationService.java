package com.musicapp.recommendation;

import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.recommendation.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private static final int REBUILD_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 25;

    private final ListenerRepository listenerRepository;
    private final JdbcOperations jdbcTemplate;

    @Transactional
    public Page<RecommendationResponse> getRecommendations (String username, int page, int size) {
        final Long listenerId = requireListenerId(username);
        validatePage(page, size);
        if (recommendationsNeedRebuild(listenerId)) rebuild(listenerId);
        return findRecommendations(listenerId, page, size);
    }

    @Transactional
    public List<RecommendationResponse> getRecommendations (String username, int limit) {
        return getRecommendations(username, 0, limit).getContent();
    }

    @Transactional
    public Page<RecommendationResponse> rebuildRecommendations (String username, int page, int size) {
        final Long listenerId = requireListenerId(username);
        validatePage(page, size);
        rebuild(listenerId);
        return findRecommendations(listenerId, page, size);
    }

    public List<RecommendationResponse> rebuildRecommendations (String username, int limit) {
        return rebuildRecommendations(username, 0, limit).getContent();
    }

    private void rebuild (Long listenerId) {
        jdbcTemplate.update("""
                DELETE FROM listener_recommendation
                WHERE listener_id = ?
                """, listenerId);

        jdbcTemplate.update("""
                WITH selected_listener AS (
                    SELECT ?::bigint AS listener_id
                ),
                genre_signal AS (
                    SELECT
                        sg.genre_name,
                        SUM(
                            lsa.stream_count * 1.5
                            - lsa.skip_count * 3.0
                            + CASE lsa.attitude::text
                                WHEN 'like' THEN 35
                                WHEN 'dislike' THEN -25
                                WHEN 'not_interested' THEN -40
                                ELSE 0
                              END
                        ) AS score
                    FROM selected_listener selected
                    JOIN listener_song_activity lsa
                        ON lsa.listener_id = selected.listener_id
                    JOIN song_genre sg
                        ON sg.song_id = lsa.song_id
                    GROUP BY sg.genre_name
                ),
                performer_signal AS (
                    SELECT
                        ps.performer_id,
                        SUM(
                            lsa.stream_count * 2.0
                            - lsa.skip_count * 4.0
                            + CASE lsa.attitude::text
                                WHEN 'like' THEN 45
                                WHEN 'dislike' THEN -45
                                WHEN 'not_interested' THEN -65
                                ELSE 0
                              END
                        ) AS score
                    FROM selected_listener selected
                    JOIN listener_song_activity lsa
                        ON lsa.listener_id = selected.listener_id
                    JOIN performer_song ps
                        ON ps.song_id = lsa.song_id
                    GROUP BY ps.performer_id
                ),
                song_genre_score AS (
                    SELECT
                        s.song_id,
                        SUM(
                            COALESCE(lgp.priority_score, 0) * 0.7
                            + CASE WHEN lpg.genre_name IS NULL THEN 0 ELSE 20 END
                            + COALESCE(gs.score, 0) * 0.2
                        ) AS score
                    FROM selected_listener selected
                    CROSS JOIN song s
                    LEFT JOIN song_genre sg
                        ON sg.song_id = s.song_id
                    LEFT JOIN listener_genre_priority lgp
                        ON lgp.listener_id = selected.listener_id
                       AND lgp.genre_name = sg.genre_name
                    LEFT JOIN listener_preferred_genre lpg
                        ON lpg.listener_id = selected.listener_id
                       AND lpg.genre_name = sg.genre_name
                    LEFT JOIN genre_signal gs
                        ON gs.genre_name = sg.genre_name
                    GROUP BY s.song_id
                ),
                song_performer_score AS (
                    SELECT
                        ps.song_id,
                        SUM(
                            CASE WHEN lfp.performer_id IS NULL THEN 0 ELSE 50 END
                            + CASE lpa.attitude::text
                                WHEN 'like' THEN 45
                                WHEN 'dislike' THEN -55
                                WHEN 'not_interested' THEN -75
                                ELSE 0
                              END
                            + COALESCE(psig.score, 0) * 0.25
                        ) AS score
                    FROM selected_listener selected
                    CROSS JOIN performer_song ps
                    LEFT JOIN listener_following_performer lfp
                        ON lfp.listener_id = selected.listener_id
                       AND lfp.performer_id = ps.performer_id
                    LEFT JOIN listener_performer_attitude lpa
                        ON lpa.listener_id = selected.listener_id
                       AND lpa.performer_id = ps.performer_id
                    LEFT JOIN performer_signal psig
                        ON psig.performer_id = ps.performer_id
                    GROUP BY ps.song_id
                ),
                blocked_artist_penalty AS (
                    SELECT candidate_song.song_id,
                           COUNT(DISTINCT blocked_song.song_id) * 20.0 AS penalty
                    FROM selected_listener selected
                    JOIN blocked_song blocked_song ON blocked_song.listener_id = selected.listener_id
                    JOIN performer_song blocked_song_performer ON blocked_song_performer.song_id = blocked_song.song_id
                    JOIN performer_song candidate_song ON candidate_song.performer_id = blocked_song_performer.performer_id
                    GROUP BY candidate_song.song_id
                ),
                candidate_score AS (
                    SELECT
                        s.song_id,
                        COALESCE(sgs.score, 0) + COALESCE(sps.score, 0) - COALESCE(bap.penalty, 0) AS score
                    FROM selected_listener selected
                    CROSS JOIN song s
                    LEFT JOIN song_genre_score sgs
                        ON sgs.song_id = s.song_id
                    LEFT JOIN song_performer_score sps
                        ON sps.song_id = s.song_id
                    LEFT JOIN blocked_artist_penalty bap
                        ON bap.song_id = s.song_id
                    WHERE NOT EXISTS (
                            SELECT 1
                            FROM listener_song_activity lsa
                            WHERE lsa.listener_id = selected.listener_id
                              AND lsa.song_id = s.song_id
                        )
                      AND NOT EXISTS (
                            SELECT 1
                            FROM blocked_song bs
                            WHERE bs.listener_id = selected.listener_id
                              AND bs.song_id = s.song_id
                        )
                      AND NOT EXISTS (
                            SELECT 1
                            FROM blocked_performer bp
                            JOIN performer_song blocked_song_performer
                                ON blocked_song_performer.performer_id = bp.performer_id
                            WHERE bp.listener_id = selected.listener_id
                              AND blocked_song_performer.song_id = s.song_id
                        )
                )
                INSERT INTO listener_recommendation (listener_id, song_id, recommendation_score, generated_at)
                SELECT
                    selected.listener_id,
                    candidate.song_id,
                    ROUND(candidate.score::numeric, 4),
                    CURRENT_TIMESTAMP
                FROM selected_listener selected
                JOIN candidate_score candidate
                    ON candidate.score > 0
                ORDER BY candidate.score DESC, candidate.song_id
                LIMIT ?
                ON CONFLICT (listener_id, song_id) DO UPDATE SET
                    recommendation_score = EXCLUDED.recommendation_score,
                    generated_at = EXCLUDED.generated_at
                """, listenerId, REBUILD_LIMIT);
    }

    private void validatePage (int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("Illegal recommendation page or size.");
    }

    private Page<RecommendationResponse> findRecommendations (Long listenerId, int page, int size) {
        final List<RecommendationResponse> content = jdbcTemplate.query("""
                SELECT
                    lr.recommendation_score,
                    lr.generated_at,
                    s.song_id,
                    s.title,
                    s.song_url,
                    s.release_date,
                    s.credits,
                    s.money_per_stream,
                    p.performer_id AS main_performer_id,
                    p.nickname AS main_performer_name,
                    a.album_id,
                    a.album_name,
                    a.release_date AS album_release_date,
                    ARRAY_REMOVE(ARRAY_AGG(DISTINCT sg.genre_name ORDER BY sg.genre_name), NULL) AS genres
                FROM listener_recommendation lr
                JOIN song s ON s.song_id = lr.song_id
                JOIN performer p ON p.performer_id = s.main_performer_id
                LEFT JOIN album a ON a.album_id = s.album_id
                LEFT JOIN song_genre sg ON sg.song_id = s.song_id
                WHERE lr.listener_id = ?
                GROUP BY lr.listener_id, lr.song_id, lr.recommendation_score, lr.generated_at,
                         s.song_id, p.performer_id, a.album_id
                ORDER BY lr.recommendation_score DESC, lr.generated_at DESC, s.title
                LIMIT ? OFFSET ?
                """, this::mapRecommendation, listenerId, size, page * size);
        return new PageImpl<>(content, PageRequest.of(page, size), recommendationCount(listenerId));
    }

    private long recommendationCount (Long listenerId) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listener_recommendation WHERE listener_id = ?", Long.class, listenerId); }

    private boolean recommendationsNeedRebuild (Long listenerId) {
        if (recommendationCount(listenerId) == 0) return true;
        final Long streamsSinceLastRebuild = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM song_stream
                WHERE listener_id = ?
                  AND streamed_at > (
                      SELECT MAX(generated_at)
                      FROM listener_recommendation
                      WHERE listener_id = ?
                  )
                """, Long.class, listenerId, listenerId);
        return streamsSinceLastRebuild != null && streamsSinceLastRebuild >= REBUILD_LIMIT;
    }

    private Long requireListenerId (String username) {
        return listenerRepository.findByUsername(username)
                .map(Listener::getId)
                .orElseThrow(() -> new NotFoundException("Listener not found: " + username));
    }

    private RecommendationResponse mapRecommendation (ResultSet rs, int rowNum) throws SQLException {
        return new RecommendationResponse(
                rs.getBigDecimal("recommendation_score"),
                toInstant(rs, "generated_at"),
                new SongResponse(
                        rs.getLong("song_id"),
                        rs.getString("title"),
                        rs.getString("song_url"),
                        rs.getDate("release_date").toLocalDate(),
                        rs.getString("credits"),
                        rs.getObject("money_per_stream", BigDecimal.class),
                        new PerformerSummary(
                                rs.getLong("main_performer_id"),
                                rs.getString("main_performer_name")
                        ),
                        albumSummary(rs),
                        genres(rs)
                )
        );
    }

    private Instant toInstant (ResultSet rs, String columnName) throws SQLException {
        final Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private AlbumSummary albumSummary (ResultSet rs) throws SQLException {
        final Long albumId = rs.getObject("album_id", Long.class);
        if (albumId == null) {
            return null;
        }

        return new AlbumSummary(
                albumId,
                rs.getString("album_name"),
                rs.getDate("album_release_date").toLocalDate()
        );
    }

    private List<String> genres (ResultSet rs) throws SQLException {
        final Array genres = rs.getArray("genres");
        if (genres == null) {
            return List.of();
        }

        return Arrays.asList((String[]) genres.getArray());
    }
}
