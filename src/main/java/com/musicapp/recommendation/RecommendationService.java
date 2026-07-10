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
    private static final int MAX_LIMIT = 100;

    private final ListenerRepository listenerRepository;
    private final JdbcOperations jdbcTemplate;

    @Transactional(readOnly = true)
    public List<RecommendationResponse> getRecommendations (String username, int limit) {
        final Long listenerId = requireListenerId(username);
        final int normalizedLimit = normalizeLimit(limit);

        return jdbcTemplate.query("""
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
                LIMIT ?
                """, this::mapRecommendation, listenerId, normalizedLimit);
    }

    private Long requireListenerId (String username) {
        return listenerRepository.findByUsername(username)
                .map(Listener::getId)
                .orElseThrow(() -> new NotFoundException("Listener not found: " + username));
    }

    private int normalizeLimit (int limit) {
        if (limit < 1) {
            throw new BadRequestException("Recommendation limit must be at least 1.");
        }
        return Math.min(limit, MAX_LIMIT);
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
