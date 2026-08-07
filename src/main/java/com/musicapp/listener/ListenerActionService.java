package com.musicapp.listener;

import com.musicapp.catalog.*;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.dto.ListeningHistoryResponse;
import com.musicapp.listener.dto.PerformerActionResponse;
import com.musicapp.listener.dto.SongActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ListenerActionService {
    private final ListenerRepository listenerRepository;
    private final SongRepository songRepository;
    private final PerformerRepository performerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CatalogService catalogService;

    @Transactional(readOnly = true)
    public List<SongResponse> getFavoriteSongs (String username) {
        final Long listenerId = requireListenerId(username);
        final List<Long> songIds = jdbcTemplate.queryForList("""
                SELECT song_id
                FROM listener_song_activity
                WHERE listener_id = ?
                  AND attitude = 'like'::attitude
                ORDER BY updated_at DESC
                """, Long.class, listenerId);

        if (songIds.isEmpty()) {
            return List.of();
        }

        final Map<Long, SongResponse> songsById = catalogService.getSongsByIds(songIds);

        return songIds.stream().map(songsById::get).filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public SongActionResponse getSongState (String username, Long songId) {
        final Long listenerId = requireListenerIdForExistingSong(username, songId);
        return findSongState(listenerId, songId);
    }

    @Transactional
    public SongActionResponse streamSong (String username, Long songId, boolean skipped) {
        final Long listenerId = requireListenerIdForExistingSong(username, songId);

        jdbcTemplate.update("""
                INSERT INTO song_stream (listener_id, song_id, skipped)
                VALUES (?, ?, ?)
                """, listenerId, songId, skipped);

        jdbcTemplate.update("""
                INSERT INTO listener_song_activity (listener_id, song_id, stream_count, skip_count, updated_at)
                VALUES (?, ?, 1, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (listener_id, song_id) DO UPDATE SET
                    stream_count = listener_song_activity.stream_count + 1,
                    skip_count = listener_song_activity.skip_count + EXCLUDED.skip_count,
                    updated_at = CURRENT_TIMESTAMP
                """, listenerId, songId, skipped ? 1 : 0);

        return findSongState(listenerId, songId);
    }

    @Transactional
    public SongActionResponse setSongAttitude (String username, Long songId, ListenerAttitude attitude) {
        final Long listenerId = requireListenerIdForExistingSong(username, songId);

        jdbcTemplate.update("""
                INSERT INTO listener_song_activity (listener_id, song_id, attitude, updated_at)
                VALUES (?, ?, ?::attitude, CURRENT_TIMESTAMP)
                ON CONFLICT (listener_id, song_id) DO UPDATE SET
                    attitude = EXCLUDED.attitude,
                    updated_at = CURRENT_TIMESTAMP
                """, listenerId, songId, attitude.name());
        markRecommendationsStale(listenerId);

        return findSongState(listenerId, songId);
    }

    @Transactional
    public SongActionResponse clearSongAttitude (String username, Long songId) {
        final Long listenerId = requireListenerIdForExistingSong(username, songId);

        jdbcTemplate.update("""
                UPDATE listener_song_activity
                SET attitude = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE listener_id = ?
                  AND song_id = ?
                """, listenerId, songId);
        markRecommendationsStale(listenerId);

        return findSongState(listenerId, songId);
    }

    @Transactional
    public SongActionResponse blockSong (String username, Long songId) {
        final Long listenerId = requireListenerIdForExistingSong(username, songId);

        jdbcTemplate.update("""
                INSERT INTO blocked_song (listener_id, song_id)
                VALUES (?, ?)
                ON CONFLICT (listener_id, song_id) DO NOTHING
                """, listenerId, songId);

        markRecommendationsStale(listenerId);

        return findSongState(listenerId, songId);
    }

    @Transactional
    public SongActionResponse unblockSong (String username, Long songId) {
        final Long listenerId = requireListenerIdForExistingSong(username, songId);

        jdbcTemplate.update("""
                DELETE FROM blocked_song
                WHERE listener_id = ?
                  AND song_id = ?
                """, listenerId, songId);

        markRecommendationsStale(listenerId);

        return findSongState(listenerId, songId);
    }

    private void markRecommendationsStale (Long listenerId) {
        jdbcTemplate.update("DELETE FROM listener_recommendation WHERE listener_id = ?", listenerId);
    }

    private Long requireListenerIdForExistingSong (String username, Long songId) {
        final Long listenerId = requireListenerId(username);

        if (!songRepository.existsById(songId)) {
            throw new NotFoundException("Song not found: " + songId);
        }

        return listenerId;
    }

    @Transactional(readOnly = true)
    public Page<ListeningHistoryResponse> getListeningHistory (Pageable pageable, String username, Instant from, Instant to, Boolean skipped) {
        ensureRangeValid(from, to);

        final Long listenerId = requireListenerId(username);

        StringBuilder sql = new StringBuilder("""
                    SELECT streamed_at, skipped, song_id
                    FROM song_stream
                    WHERE listener_id = ?
                """);

        List<Object> params = appendHistoryFilters(from, to, skipped, listenerId, sql);

        sql.append("""
                    ORDER BY streamed_at DESC
                    LIMIT ? OFFSET ?
                """);

        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());

        final List<HistoryRow> historyRows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new HistoryRow(rs.getLong("song_id"), rs.getTimestamp("streamed_at").toInstant(), rs.getBoolean("skipped")), params.toArray());
        final List<Long> songIds = historyRows.stream().map(HistoryRow::songId).distinct().toList();
        final Map<Long, SongResponse> songs = catalogService.getSongsByIds(songIds);

        final List<ListeningHistoryResponse> content = historyRows.stream().map(row -> new ListeningHistoryResponse(songs.get(row.songId()), row.playedAt(), row.skipped())).toList();

        StringBuilder countSql = new StringBuilder("""
                    SELECT COUNT(*)
                    FROM song_stream
                    WHERE listener_id = ?
                """);

        final List<Object> countParams = appendHistoryFilters(from, to, skipped, listenerId, countSql);
        final Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

        return new PageImpl<>(content, pageable, total);
    }

    private void ensureRangeValid (Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
    }

    private List<Object> appendHistoryFilters (Instant from, Instant to, Boolean skipped, Long listenerId, StringBuilder sql) {
        List<Object> params = new ArrayList<>();
        params.add(listenerId);

        if (from != null) {
            sql.append(" AND streamed_at >= ?");
            params.add(from);
        }

        if (to != null) {
            sql.append(" AND streamed_at <= ?");
            params.add(to);
        }

        if (skipped != null) {
            sql.append(" AND skipped = ?");
            params.add(skipped);
        }

        return params;
    }

    private record HistoryRow(Long songId, Instant playedAt, boolean skipped) {
    }

    @Transactional
    public void deleteListeningHistory (String username) {
        final Long listenerId = requireListenerId(username);

        jdbcTemplate.update("""
                DELETE
                FROM song_stream
                WHERE listener_id = ?
                """, listenerId);
    }

    @Transactional(readOnly = true)
    public PerformerActionResponse getPerformerState (String username, Long performerId) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);
        return findPerformerState(listenerId, performerId);
    }

    @Transactional
    public PerformerActionResponse followPerformer (String username, Long performerId) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);

        jdbcTemplate.update("""
                INSERT INTO listener_following_performer (listener_id, performer_id)
                VALUES (?, ?)
                ON CONFLICT (listener_id, performer_id) DO NOTHING
                """, listenerId, performerId);

        return findPerformerState(listenerId, performerId);
    }

    @Transactional
    public PerformerActionResponse unfollowPerformer (String username, Long performerId) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);

        jdbcTemplate.update("""
                DELETE FROM listener_following_performer
                WHERE listener_id = ?
                  AND performer_id = ?
                """, listenerId, performerId);

        return findPerformerState(listenerId, performerId);
    }

    @Transactional
    public PerformerActionResponse setPerformerAttitude (String username, Long performerId, ListenerAttitude attitude) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);

        jdbcTemplate.update("""
                INSERT INTO listener_performer_attitude (listener_id, performer_id, attitude, updated_at)
                VALUES (?, ?, ?::attitude, CURRENT_TIMESTAMP)
                ON CONFLICT (listener_id, performer_id) DO UPDATE SET
                    attitude = EXCLUDED.attitude,
                    updated_at = CURRENT_TIMESTAMP
                """, listenerId, performerId, attitude.name());

        return findPerformerState(listenerId, performerId);
    }

    @Transactional
    public PerformerActionResponse clearPerformerAttitude (String username, Long performerId) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);

        jdbcTemplate.update("""
                DELETE FROM listener_performer_attitude
                WHERE listener_id = ?
                  AND performer_id = ?
                """, listenerId, performerId);

        return findPerformerState(listenerId, performerId);
    }

    @Transactional
    public PerformerActionResponse blockPerformer (String username, Long performerId) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);

        jdbcTemplate.update("""
                INSERT INTO blocked_performer (listener_id, performer_id)
                VALUES (?, ?)
                ON CONFLICT (listener_id, performer_id) DO NOTHING
                """, listenerId, performerId);

        return findPerformerState(listenerId, performerId);
    }

    @Transactional
    public PerformerActionResponse unblockPerformer (String username, Long performerId) {
        final Long listenerId = requireListenerIdForExistingPerformer(username, performerId);

        jdbcTemplate.update("""
                DELETE FROM blocked_performer
                WHERE listener_id = ?
                  AND performer_id = ?
                """, listenerId, performerId);

        return findPerformerState(listenerId, performerId);
    }

    private Long requireListenerIdForExistingPerformer (String username, Long performerId) {
        final Long listenerId = requireListenerId(username);

        if (!performerRepository.existsById(performerId)) {
            throw new NotFoundException("Performer not found: " + performerId);
        }

        return listenerId;
    }

    private Long requireListenerId (String username) {
        return listenerRepository.findByUsername(username).map(Listener::getId).orElseThrow(() -> new NotFoundException("Listener not found: " + username));
    }

    private SongActionResponse findSongState (Long listenerId, Long songId) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COALESCE(a.stream_count, 0) AS stream_count,
                    COALESCE(a.skip_count, 0) AS skip_count,
                    a.attitude::text AS attitude,
                    b.blocked_at
                FROM (SELECT ?::bigint AS listener_id, ?::bigint AS song_id) selected
                LEFT JOIN listener_song_activity a
                    ON a.listener_id = selected.listener_id
                   AND a.song_id = selected.song_id
                LEFT JOIN blocked_song b
                    ON b.listener_id = selected.listener_id
                   AND b.song_id = selected.song_id
                """, (rs, rowNum) -> mapSongState(listenerId, songId, rs), listenerId, songId);
    }

    private SongActionResponse mapSongState (Long listenerId, Long songId, ResultSet rs) throws SQLException {
        final Instant blockedAt = toInstant(rs, "blocked_at");
        return new SongActionResponse(listenerId, songId, rs.getInt("stream_count"), rs.getInt("skip_count"), toAttitude(rs.getString("attitude")), blockedAt != null, blockedAt);
    }

    private PerformerActionResponse findPerformerState (Long listenerId, Long performerId) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    f.followed_at,
                    a.attitude::text AS attitude,
                    b.blocked_at
                FROM (SELECT ?::bigint AS listener_id, ?::bigint AS performer_id) selected
                LEFT JOIN listener_following_performer f
                    ON f.listener_id = selected.listener_id
                   AND f.performer_id = selected.performer_id
                LEFT JOIN listener_performer_attitude a
                    ON a.listener_id = selected.listener_id
                   AND a.performer_id = selected.performer_id
                LEFT JOIN blocked_performer b
                    ON b.listener_id = selected.listener_id
                   AND b.performer_id = selected.performer_id
                """, (rs, rowNum) -> mapPerformerState(listenerId, performerId, rs), listenerId, performerId);
    }

    private PerformerActionResponse mapPerformerState (Long listenerId, Long performerId, ResultSet rs) throws SQLException {
        final Instant followedAt = toInstant(rs, "followed_at");
        final Instant blockedAt = toInstant(rs, "blocked_at");
        return new PerformerActionResponse(listenerId, performerId, followedAt != null, toAttitude(rs.getString("attitude")), blockedAt != null, followedAt, blockedAt);
    }

    private Instant toInstant (ResultSet rs, String columnName) throws SQLException {
        final Timestamp timestamp = rs.getTimestamp(columnName);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private ListenerAttitude toAttitude (String value) {
        if (value == null) {
            return null;
        }
        return ListenerAttitude.valueOf(value);
    }
}
