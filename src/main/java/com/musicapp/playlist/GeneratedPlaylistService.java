package com.musicapp.playlist;

import com.musicapp.catalog.CatalogService;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.playlist.dto.GeneratedPlaylistResponse;
import com.musicapp.playlist.dto.GeneratedPlaylistSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class GeneratedPlaylistService {
    private static final int GENERATED_PLAYLIST_SIZE = 20;
    private static final long DAILY_REWIND_WINDOW_DAYS = 1;
    private static final long WEEKLY_REWIND_WINDOW_DAYS = 7;
    private static final long COMFORT_SONGS_WINDOW_DAYS = 365;
    private static final int NO_SKIPS_MIN_STREAMS = 10;
    private static final BigDecimal NO_SKIPS_MAX_SKIP_RATIO = new BigDecimal("0.05");
    private static final int HIDDEN_FAVOURITES_MIN_STREAMS = 20;
    private static final long FORGOTTEN_GEMS_INACTIVITY_DAYS = 30;
    private static final int FORGOTTEN_GEMS_MIN_STREAMS = 10;
    private static final long REDISCOVER_LOOKBACK_DAYS = 365;
    private static final long REDISCOVER_WINDOW_END_DAYS = 180;
    private static final long REDISCOVER_INACTIVITY_DAYS = 30;
    private static final int REDISCOVER_MIN_STREAMS = 5;
    private static final int GENERATED_PLAYLIST_TYPE_BITS = 8;

    private final ListenerRepository listenerRepository;
    private final CatalogService catalogService;
    private final GeneratedPlaylistRepository generatedPlaylistRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<GeneratedPlaylistSummaryResponse> getAvailableGeneratedPlaylists (String username) {
        final Long listenerId = requireListenerId(username);
        final Map<GeneratedPlaylistType, GeneratedPlaylist> cachedPlaylists = generatedPlaylistRepository.findAllByListenerId(listenerId).stream().collect(Collectors.toMap(GeneratedPlaylist::getPlaylistType, Function.identity()));

        return Arrays.stream(GeneratedPlaylistType.values()).map(type -> toGeneratedPlaylistSummary(type, cachedPlaylists.get(type))).toList();
    }

    private GeneratedPlaylistSummaryResponse toGeneratedPlaylistSummary (GeneratedPlaylistType type, GeneratedPlaylist playlist) {
        return new GeneratedPlaylistSummaryResponse(type, type.getDisplayName(), type.getDescription(), playlist != null && isNotExpired(playlist), playlist == null ? null : playlist.getGeneratedAt(), playlist == null ? null : playlist.getExpiresAt());
    }

    @Transactional
    public GeneratedPlaylistResponse generatePlaylist (String username, GeneratedPlaylistType type) {
        final Long listenerId = requireListenerId(username);

        acquireGeneratedPlaylistLock(listenerId, type);

        GeneratedPlaylist playlist = generatedPlaylistRepository.findByListenerIdAndPlaylistType(listenerId, type).orElse(null);

        if (playlist != null && isNotExpired(playlist)) {
            return buildCachedResponse(playlist);
        }

        List<Long> songIds = switch (type) {
            case DAILY_REWIND -> generateSongIds(listenerId, this::findDailyRewindSongs);
            case WEEKLY_REWIND -> generateSongIds(listenerId, this::findWeeklyRewindSongs);
            case ALL_TIME_REWIND -> generateSongIds(listenerId, this::findAllTimeRewindSongs);
            case FORGOTTEN_GEMS -> generateSongIds(listenerId, this::findForgottenGemsSongs);
            case COMFORT_SONGS -> generateSongIds(listenerId, this::findComfortSongs);
            case NO_SKIPS -> generateSongIds(listenerId, this::findNoSkipSongs);
            case HIDDEN_FAVOURITES -> generateSongIds(listenerId, this::findHiddenFavouritesSongs);
            case GENRE_MIX -> generateSongIds(listenerId, this::findGenreMixSongs);
            case REDISCOVER -> generateSongIds(listenerId, this::findRediscoverSongs);
        };

        final GeneratedPlaylist refreshedPlaylist = refreshPlaylist(playlist, listenerId, type, songIds);

        return buildPlaylistResponse(type, songIds, refreshedPlaylist.getGeneratedAt(), refreshedPlaylist.getExpiresAt());
    }

    private Long requireListenerId (String username) {
        return listenerRepository.findByUsername(username).map(Listener::getId).orElseThrow(() -> new NotFoundException("Listener not found: " + username));
    }

    private void acquireGeneratedPlaylistLock (Long listenerId, GeneratedPlaylistType type) {
        final long lockKey = (listenerId << GENERATED_PLAYLIST_TYPE_BITS) | (type.getLockCode() & 0xFFL);

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockKey);
                statement.execute();
            }

            return null;
        });
    }

    private boolean isNotExpired (GeneratedPlaylist playlist) {
        return Instant.now().isBefore(playlist.getExpiresAt());
    }

    private GeneratedPlaylistResponse buildCachedResponse (GeneratedPlaylist playlist) {
        List<Long> songIds = playlist.getSongs().stream().map(GeneratedPlaylistSong::getSongId).toList();

        return buildPlaylistResponse(playlist.getPlaylistType(), songIds, playlist.getGeneratedAt(), playlist.getExpiresAt());
    }

    private GeneratedPlaylist refreshPlaylist (GeneratedPlaylist playlist, Long listenerId, GeneratedPlaylistType type, List<Long> songIds) {
        final boolean isNew = playlist == null;

        if (isNew) {
            playlist = new GeneratedPlaylist();
        }

        playlist.refresh(listenerId, type, Instant.now(), songIds);

        if (isNew) {
            return generatedPlaylistRepository.save(playlist);
        }

        return playlist;
    }

    private GeneratedPlaylistResponse buildPlaylistResponse (GeneratedPlaylistType type, List<Long> songIds, Instant generatedAt, Instant expiresAt) {

        final Map<Long, SongResponse> songsById = catalogService.getSongsByIds(songIds);

        final List<SongResponse> songs = songIds.stream().map(songsById::get).filter(Objects::nonNull).toList();

        return new GeneratedPlaylistResponse(type, type.getDisplayName(), type.getDescription(), generatedAt, expiresAt, songs);
    }

    private List<Long> generateSongIds (Long listenerId, Function<Long, List<Long>> songSupplier) {

        List<Long> songIds = songSupplier.apply(listenerId).stream().distinct().toList();

        return completePlaylist(listenerId, songIds, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> completePlaylist (Long listenerId, List<Long> playlist, int targetSize) {
        final LinkedHashSet<Long> songs = new LinkedHashSet<>(playlist);

        if (songs.size() < targetSize) {
            songs.addAll(findGenreRecommendations(listenerId, songs, targetSize - songs.size()));
        }

        if (songs.size() < targetSize) {
            songs.addAll(findPopularRecommendations(listenerId, songs, targetSize - songs.size()));
        }

        return songs.stream().limit(targetSize).toList();
    }

    private List<Long> findGenreRecommendations (Long listenerId, Set<Long> excludedSongIds, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.song_id
                FROM song s
                JOIN song_genre sg
                    ON sg.song_id = s.song_id
                JOIN listener_genre_priority lgp
                    ON lgp.genre_name = sg.genre_name
                LEFT JOIN song_stream ss
                    ON ss.song_id = s.song_id
                WHERE lgp.listener_id = ?
                """);

        List<Object> params = new ArrayList<>();

        if (!excludedSongIds.isEmpty()) {
            sql.append("""
                          AND s.song_id NOT IN (
                    """);
            sql.append(placeholders(excludedSongIds.size()));
            sql.append(")\n");

            params.addAll(excludedSongIds);
        }

        sql.append("""
                GROUP BY s.song_id
                ORDER BY MAX(lgp.priority_score) DESC,
                         COUNT(ss.song_id) DESC,
                         MAX(ss.streamed_at) DESC,
                         MAX(s.song_id)
                LIMIT ?
                """);

        params.add(limit);

        return findListenerSongs(listenerId, sql.toString(), params.toArray());
    }

    private List<Long> findPopularRecommendations (Long listenerId, Set<Long> excludedSongIds, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.song_id
                FROM song s
                LEFT JOIN song_stream ss
                    ON ss.song_id = s.song_id
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        if (!excludedSongIds.isEmpty()) {
            sql.append("""
                          AND s.song_id NOT IN (
                    """);
            sql.append(placeholders(excludedSongIds.size()));
            sql.append(") ");

            params.addAll(excludedSongIds);
        }

        sql.append("""
                GROUP BY s.song_id
                ORDER BY COUNT(ss.song_id) DESC,
                         MAX(ss.streamed_at) DESC
                LIMIT ?
                """);

        params.add(limit);

        return findGlobalSongs(listenerId, sql.toString(), params.toArray());
    }

    private List<Long> findGlobalSongs (Long listenerId, String sql, Object... params) {
        return executeFilteredSongQuery(listenerId, sql, params);
    }

    private String placeholders (int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private List<Long> findDailyRewindSongs (Long listenerId) {
        final Instant now = Instant.now();

        return findTopSongs(listenerId, now.minus(DAILY_REWIND_WINDOW_DAYS, ChronoUnit.DAYS), now, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findWeeklyRewindSongs (Long listenerId) {
        final Instant now = Instant.now();

        return findTopSongs(listenerId, now.minus(WEEKLY_REWIND_WINDOW_DAYS, ChronoUnit.DAYS), now, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findComfortSongs (Long listenerId) {
        final Instant now = Instant.now();

        return findTopSongs(listenerId, now.minus(COMFORT_SONGS_WINDOW_DAYS, ChronoUnit.DAYS), now, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findNoSkipSongs (Long listenerId) {
        final String sql = """
                SELECT song_id
                FROM listener_song_activity
                WHERE listener_id = ?
                  AND stream_count >= ?
                  AND skip_count::decimal / stream_count <= ?
                ORDER BY stream_count DESC
                LIMIT ?
                """;

        return findListenerSongs(listenerId, sql, NO_SKIPS_MIN_STREAMS, NO_SKIPS_MAX_SKIP_RATIO, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findHiddenFavouritesSongs (Long listenerId) {
        final String sql = """
                SELECT song_id
                FROM listener_song_activity
                WHERE listener_id = ?
                  AND stream_count >= ?
                  AND attitude IS DISTINCT FROM 'like'
                ORDER BY stream_count DESC
                LIMIT ?
                """;

        return findListenerSongs(listenerId, sql, HIDDEN_FAVOURITES_MIN_STREAMS, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findAllTimeRewindSongs (Long listenerId) {
        return findTopSongs(listenerId, Instant.EPOCH, Instant.now(), GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findGenreMixSongs (Long listenerId) {
        final String sql = """
                SELECT s.song_id
                FROM song s
                JOIN song_genre sg
                    ON sg.song_id = s.song_id
                JOIN listener_genre_priority lgp
                    ON lgp.genre_name = sg.genre_name
                WHERE lgp.listener_id = ?
                GROUP BY s.song_id
                ORDER BY MAX(lgp.priority_score) DESC,
                         COUNT(*) DESC,
                         MAX(s.song_id)
                LIMIT ?
                """;

        return findListenerSongs(listenerId, sql, GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findForgottenGemsSongs (Long listenerId) {
        final Instant inactivityCutoff = Instant.now().minus(FORGOTTEN_GEMS_INACTIVITY_DAYS, ChronoUnit.DAYS);

        final String sql = """
                SELECT song_id
                FROM song_stream
                WHERE listener_id = ?
                  AND streamed_at < ?
                GROUP BY song_id
                HAVING COUNT(*) >= ?
                   AND MAX(streamed_at) < ?
                ORDER BY COUNT(*) DESC
                LIMIT ?
                """;

        return findListenerSongs(listenerId, sql, Timestamp.from(inactivityCutoff), FORGOTTEN_GEMS_MIN_STREAMS, Timestamp.from(inactivityCutoff), GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findRediscoverSongs (Long listenerId) {
        final Instant lookbackStart = Instant.now().minus(REDISCOVER_LOOKBACK_DAYS, ChronoUnit.DAYS);
        final Instant windowEnd = Instant.now().minus(REDISCOVER_WINDOW_END_DAYS, ChronoUnit.DAYS);
        final Instant inactivityCutoff = Instant.now().minus(REDISCOVER_INACTIVITY_DAYS, ChronoUnit.DAYS);

        final String sql = """
                SELECT song_id
                FROM song_stream
                WHERE listener_id = ?
                  AND streamed_at BETWEEN ? AND ?
                GROUP BY song_id
                HAVING COUNT(*) >= ?
                   AND MAX(streamed_at) < ?
                ORDER BY COUNT(*) DESC
                LIMIT ?
                """;

        return findListenerSongs(listenerId, sql, Timestamp.from(lookbackStart), Timestamp.from(windowEnd), REDISCOVER_MIN_STREAMS, Timestamp.from(inactivityCutoff), GENERATED_PLAYLIST_SIZE);
    }

    private List<Long> findTopSongs (Long listenerId, Instant from, Instant to, int limit) {
        final String sql = """
                SELECT song_id
                FROM song_stream
                WHERE listener_id = ?
                AND streamed_at >= ?
                AND streamed_at < ?
                GROUP BY song_id
                ORDER BY COUNT(*) DESC, MAX(streamed_at) DESC
                LIMIT ?
                """;

        return findListenerSongs(listenerId, sql, from, to, limit);
    }

    private List<Long> findListenerSongs (Long listenerId, String sql, Object... params) {
        final Object[] queryParams = new Object[(params == null ? 0 : params.length) + 1];

        queryParams[0] = listenerId;

        if (params != null && params.length > 0) {
            System.arraycopy(params, 0, queryParams, 1, params.length);
        }

        return executeFilteredSongQuery(listenerId, sql, queryParams);
    }

    private List<Long> executeFilteredSongQuery (Long listenerId, String sql, Object[] queryParams) {
        final String filteredSql = """
                                           SELECT s.song_id
                                           FROM (
                                           """ + sql + """
                                           ) s
                                           JOIN song song
                                               ON song.song_id = s.song_id
                                           WHERE NOT EXISTS (
                                               SELECT 1
                                               FROM blocked_song bs
                                               WHERE bs.listener_id = ?
                                                 AND bs.song_id = s.song_id
                                           )
                                             AND NOT EXISTS (
                                               SELECT 1
                                               FROM blocked_performer bp
                                               WHERE bp.listener_id = ?
                                               AND EXISTS (
                                                   SELECT 1
                                                   FROM performer_song ps
                                                   WHERE ps.performer_id = bp.performer_id
                                                     AND ps.song_id = song.song_id
                                               )
                                           )
                                           """;

        final Object[] finalParams = new Object[(queryParams == null ? 0 : queryParams.length) + 2];

        if (queryParams != null && queryParams.length > 0) {
            System.arraycopy(queryParams, 0, finalParams, 0, queryParams.length);
        }

        finalParams[finalParams.length - 2] = listenerId;
        finalParams[finalParams.length - 1] = listenerId;

        return jdbcTemplate.queryForList(filteredSql, Long.class, finalParams);
    }
}
