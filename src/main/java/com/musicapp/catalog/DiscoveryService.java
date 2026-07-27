package com.musicapp.catalog;

import com.musicapp.catalog.dto.SearchSuggestionResponse;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.catalog.dto.TrendingSongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiscoveryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TRENDING_DAYS = 90;
    private static final int MAX_SUGGESTIONS = 10;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_LENGTH = 80;

    private final ListenerRepository listenerRepository;
    private final CatalogService catalogService;
    private final JdbcOperations jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<TrendingSongResponse> getTrendingSongs (int page, int size, int days) {
        final PageRequest pageable = createPageable(page, size);
        ensureValidTrendingDays(days);

        final List<TrendingRow> rows = jdbcTemplate.query("""
                SELECT song_id,
                       COUNT(*) AS stream_count,
                       COUNT(DISTINCT listener_id) AS listener_count,
                       MAX(streamed_at) AS latest_stream
                FROM song_stream
                WHERE skipped = FALSE
                  AND streamed_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                GROUP BY song_id
                ORDER BY stream_count DESC, listener_count DESC, latest_stream DESC, song_id
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new TrendingRow(rs.getLong("song_id"), rs.getLong("stream_count"), rs.getLong("listener_count")), days, size, pageable.getOffset());

        final Map<Long, SongResponse> songs = catalogService.getSongsByIds(rows.stream().map(TrendingRow::songId).toList());
        final List<TrendingSongResponse> content = rows.stream().filter(row -> songs.containsKey(row.songId())).map(row -> new TrendingSongResponse(songs.get(row.songId()), row.streamCount(), row.listenerCount())).toList();

        final Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT song_id
                    FROM song_stream
                    WHERE skipped = FALSE
                      AND streamed_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                    GROUP BY song_id
                ) trending
                """, Long.class, days);

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private void ensureValidTrendingDays (int days) {
        if (days < 1 || days > MAX_TRENDING_DAYS) {
            throw new BadRequestException("Trending days must be between 1 and " + MAX_TRENDING_DAYS + ".");
        }
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionResponse> getSearchSuggestions (String query, int limit) {
        ensureValidSuggestionLimit(limit);

        final String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        if (normalizedQuery.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text cannot exceed " + MAX_SEARCH_LENGTH + " characters.");
        }

        return jdbcTemplate.query("""
                WITH search_input AS (
                    SELECT LOWER(?) AS search_term
                ),
                candidates AS (
                    SELECT 'performer' AS type,
                           performer.performer_id AS entity_id,
                           performer.nickname AS title,
                           INITCAP(REPLACE(performer.performer_type::text, '_', ' ')) AS subtitle
                    FROM performer
                    CROSS JOIN search_input
                    WHERE LOWER(performer.nickname) LIKE '%' || search_input.search_term || '%'

                    UNION ALL

                    SELECT 'album' AS type,
                           album.album_id AS entity_id,
                           album.album_name AS title,
                           performer.nickname || ' · Album' AS subtitle
                    FROM album
                    JOIN performer ON performer.performer_id = album.performer_id
                    CROSS JOIN search_input
                    WHERE LOWER(album.album_name) LIKE '%' || search_input.search_term || '%'
                       OR LOWER(performer.nickname) LIKE '%' || search_input.search_term || '%'

                    UNION ALL

                    SELECT 'song' AS type,
                           song.song_id AS entity_id,
                           song.title,
                           performer.nickname || ' · Song' AS subtitle
                    FROM song
                    JOIN performer ON performer.performer_id = song.main_performer_id
                    CROSS JOIN search_input
                    WHERE LOWER(song.title) LIKE '%' || search_input.search_term || '%'
                       OR LOWER(performer.nickname) LIKE '%' || search_input.search_term || '%'
                )
                SELECT type, entity_id, title, subtitle
                FROM candidates
                CROSS JOIN search_input
                ORDER BY CASE
                             WHEN LOWER(title) = search_input.search_term THEN 0
                             WHEN LOWER(title) LIKE search_input.search_term || '%' THEN 1
                             ELSE 2
                         END,
                         CASE type
                             WHEN 'performer' THEN 0
                             WHEN 'album' THEN 1
                             ELSE 2
                         END,
                         LOWER(title),
                         entity_id
                LIMIT ?
                """, (rs, rowNum) -> new SearchSuggestionResponse(rs.getString("type"), rs.getLong("entity_id"), rs.getString("title"), rs.getString("subtitle")), normalizedQuery, limit);
    }

    private void ensureValidSuggestionLimit (int limit) {
        if (limit < 1 || limit > MAX_SUGGESTIONS) {
            throw new BadRequestException("Suggestion limit must be between 1 and " + MAX_SUGGESTIONS + ".");
        }
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> getFollowedPerformerReleases (String username, int page, int size) {
        final PageRequest pageable = createPageable(page, size);
        final Long listenerId = requireListenerId(username);
        final List<Long> songIds = jdbcTemplate.queryForList("""
                SELECT s.song_id
                FROM listener_following_performer followed
                JOIN song s ON s.main_performer_id = followed.performer_id
                LEFT JOIN blocked_song blocked_song
                       ON blocked_song.listener_id = followed.listener_id
                      AND blocked_song.song_id = s.song_id
                LEFT JOIN blocked_performer blocked_performer
                       ON blocked_performer.listener_id = followed.listener_id
                      AND blocked_performer.performer_id = followed.performer_id
                WHERE followed.listener_id = ?
                  AND blocked_song.song_id IS NULL
                  AND blocked_performer.performer_id IS NULL
                ORDER BY s.release_date DESC, s.title, s.song_id
                LIMIT ? OFFSET ?
                """, Long.class, listenerId, size, pageable.getOffset());

        final Map<Long, SongResponse> songs = catalogService.getSongsByIds(songIds);
        final List<SongResponse> content = songIds.stream().map(songs::get).filter(java.util.Objects::nonNull).toList();
        final Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM listener_following_performer followed
                JOIN song s ON s.main_performer_id = followed.performer_id
                LEFT JOIN blocked_song blocked_song
                       ON blocked_song.listener_id = followed.listener_id
                      AND blocked_song.song_id = s.song_id
                LEFT JOIN blocked_performer blocked_performer
                       ON blocked_performer.listener_id = followed.listener_id
                      AND blocked_performer.performer_id = followed.performer_id
                WHERE followed.listener_id = ?
                  AND blocked_song.song_id IS NULL
                  AND blocked_performer.performer_id IS NULL
                """, Long.class, listenerId);

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private PageRequest createPageable (int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Illegal discovery page or size.");
        }
        return PageRequest.of(page, size);
    }

    private Long requireListenerId (String username) {
        return listenerRepository.findByUsername(username).map(Listener::getId).orElseThrow(() -> new NotFoundException("Listener not found: " + username));
    }

    private record TrendingRow(Long songId, long streamCount, long listenerCount) {
    }
}
