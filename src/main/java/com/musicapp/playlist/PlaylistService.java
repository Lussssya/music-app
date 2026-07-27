package com.musicapp.playlist;

import com.musicapp.catalog.CatalogService;
import com.musicapp.catalog.SongRepository;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.playlist.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PlaylistService {
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

    private final ListenerRepository listenerRepository;
    private final SongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CatalogService catalogService;
    private final GeneratedPlaylistRepository generatedPlaylistRepository;

    @Transactional(readOnly = true)
    public List<PlaylistSummaryResponse> findPlaylists (String search, PlaylistType type, Long creatorId, Long memberId) {
        return jdbcTemplate.query("""
                SELECT
                    p.playlist_id,
                    p.playlist_name,
                    p.type::text AS type,
                    p.playlist_url,
                    p.picture_url,
                    p.creator_id,
                    creator.username AS creator_username,
                    p.created_at,
                    COUNT(DISTINCT pm.listener_id)::int AS member_count,
                    COUNT(DISTINCT ps.song_id)::int AS song_count
                FROM playlist p
                JOIN listener creator ON creator.listener_id = p.creator_id
                LEFT JOIN playlist_member pm ON pm.playlist_id = p.playlist_id
                LEFT JOIN playlist_song ps ON ps.playlist_id = p.playlist_id
                WHERE (? = '' OR LOWER(p.playlist_name) LIKE LOWER(CONCAT('%', ?, '%')))
                  AND (?::text IS NULL OR p.type = ?::playlist_type)
                  AND (?::bigint IS NULL OR p.creator_id = ?)
                  AND (?::bigint IS NULL OR EXISTS (
                      SELECT 1
                      FROM playlist_member filter_member
                      WHERE filter_member.playlist_id = p.playlist_id
                        AND filter_member.listener_id = ?
                  ))
                GROUP BY p.playlist_id, creator.username
                ORDER BY p.created_at DESC, p.playlist_name
                """, this::mapPlaylistSummary, normalizeTextFilter(search), normalizeTextFilter(search), type == null ? null : type.dbValue(), type == null ? null : type.dbValue(), creatorId, creatorId, memberId, memberId);
    }

    @Transactional(readOnly = true)
    public PlaylistResponse getPlaylist (Long playlistId) {
        ensurePlaylistExists(playlistId);
        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse createPlaylist (String username, CreatePlaylistRequest request) {
        final Long creatorId = requireListenerId(username);
        final KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO playlist (playlist_name, type, playlist_url, picture_url, creator_id)
                    VALUES (?, ?::playlist_type, ?, ?, ?)
                    """, new String[]{"playlist_id"});
            statement.setString(1, request.name().trim());
            statement.setString(2, request.type().dbValue());
            statement.setString(3, blankToNull(request.playlistUrl()));
            statement.setString(4, blankToNull(request.pictureUrl()));
            statement.setLong(5, creatorId);
            return statement;
        }, keyHolder);

        Long playlistId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        jdbcTemplate.update("""
                INSERT INTO playlist_member (listener_id, playlist_id)
                VALUES (?, ?)
                """, creatorId, playlistId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse updatePlaylist (String username, Long playlistId, UpdatePlaylistRequest request) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);
        requirePlaylistCreator(listenerId, playlistId);

        jdbcTemplate.update("""
                UPDATE playlist
                SET playlist_name = ?,
                    type = ?::playlist_type,
                    playlist_url = ?,
                    picture_url = ?
                WHERE playlist_id = ?
                """, request.name().trim(), request.type().dbValue(), blankToNull(request.playlistUrl()), blankToNull(request.pictureUrl()), playlistId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public void deletePlaylist (String username, Long playlistId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);
        requirePlaylistCreator(listenerId, playlistId);

        jdbcTemplate.update("""
                DELETE FROM playlist
                WHERE playlist_id = ?
                """, playlistId);
    }

    @Transactional
    public PlaylistResponse joinPlaylist (String username, Long playlistId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);

        jdbcTemplate.update("""
                INSERT INTO playlist_member (listener_id, playlist_id)
                VALUES (?, ?)
                ON CONFLICT (listener_id, playlist_id) DO NOTHING
                """, listenerId, playlistId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse leavePlaylist (String username, Long playlistId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);

        if (isPlaylistCreator(listenerId, playlistId)) {
            throw new BadRequestException("Playlist creator cannot leave their own playlist.");
        }

        jdbcTemplate.update("""
                DELETE FROM playlist_member
                WHERE listener_id = ?
                  AND playlist_id = ?
                """, listenerId, playlistId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse addPlaylistMember (String username, Long playlistId, String memberUsername) {
        final Long creatorId = requireListenerIdForExistingPlaylist(username, playlistId);
        requirePlaylistCreator(creatorId, playlistId);
        final Long memberId = requireListenerId(memberUsername);

        jdbcTemplate.update("""
                INSERT INTO playlist_member (listener_id, playlist_id)
                VALUES (?, ?)
                ON CONFLICT (listener_id, playlist_id) DO NOTHING
                """, memberId, playlistId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse removePlaylistMember (String username, Long playlistId, String memberUsername) {
        final Long creatorId = requireListenerIdForExistingPlaylist(username, playlistId);
        requirePlaylistCreator(creatorId, playlistId);
        final Long memberId = requireListenerId(memberUsername);

        if (creatorId.equals(memberId)) {
            throw new BadRequestException("Playlist creator cannot be removed.");
        }

        jdbcTemplate.update("""
                DELETE FROM playlist_member
                WHERE listener_id = ?
                  AND playlist_id = ?
                """, memberId, playlistId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse addSong (String username, Long playlistId, Long songId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);
        requireSongExists(songId);
        requirePlaylistMember(listenerId, playlistId);

        jdbcTemplate.update("""
                INSERT INTO playlist_song (playlist_id, song_id, added_by_listener_id)
                VALUES (?, ?, ?)
                ON CONFLICT (playlist_id, song_id) DO NOTHING
                """, playlistId, songId, listenerId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse removeSong (String username, Long playlistId, Long songId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);
        requireSongExists(songId);
        requirePlaylistMember(listenerId, playlistId);

        jdbcTemplate.update("""
                DELETE FROM playlist_song
                WHERE playlist_id = ?
                  AND song_id = ?
                """, playlistId, songId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse voteForSong (String username, Long playlistId, Long songId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);
        requirePlaylistMember(listenerId, playlistId);
        requirePlaylistSong(playlistId, songId);

        jdbcTemplate.update("""
                INSERT INTO playlist_song_vote (playlist_id, song_id, listener_id)
                VALUES (?, ?, ?)
                ON CONFLICT (playlist_id, song_id, listener_id) DO NOTHING
                """, playlistId, songId, listenerId);

        return findPlaylist(playlistId);
    }

    @Transactional
    public PlaylistResponse removeSongVote (String username, Long playlistId, Long songId) {
        final Long listenerId = requireListenerIdForExistingPlaylist(username, playlistId);
        requirePlaylistMember(listenerId, playlistId);
        requirePlaylistSong(playlistId, songId);

        jdbcTemplate.update("""
                DELETE FROM playlist_song_vote
                WHERE playlist_id = ?
                  AND song_id = ?
                  AND listener_id = ?
                """, playlistId, songId, listenerId);

        return findPlaylist(playlistId);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupGeneratedPlaylists() {
        generatedPlaylistRepository.deleteExpiredPlaylists();
    }

    @Transactional(readOnly = true)
    public List<GeneratedPlaylistSummaryResponse> getAvailableGeneratedPlaylists (String username) {
        final Long listenerId = requireListenerId(username);
        final Map<GeneratedPlaylistType, GeneratedPlaylist> cachedPlaylists = generatedPlaylistRepository
                .findAllByListenerId(listenerId).stream()
                .collect(java.util.stream.Collectors.toMap(GeneratedPlaylist::getPlaylistType, Function.identity()));

        return Arrays.stream(GeneratedPlaylistType.values())
                .map(type -> toGeneratedPlaylistSummary(type, cachedPlaylists.get(type)))
                .toList();
    }

    @Transactional
    public GeneratedPlaylistResponse generatePlaylist(String username, GeneratedPlaylistType type) {
        final Long listenerId = requireListenerId(username);

        GeneratedPlaylist playlist = generatedPlaylistRepository.findByListenerIdAndPlaylistType(listenerId, type).orElse(null);

        if (playlist != null && !isExpired(playlist)) {
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

        final GeneratedPlaylist savedPlaylist = savePlaylist(playlist, listenerId, type, songIds);

        return buildPlaylistResponse(type, songIds, savedPlaylist.getGeneratedAt(), savedPlaylist.getExpiresAt());
    }

    private boolean isExpired (GeneratedPlaylist playlist) {
        return !Instant.now().isBefore(playlist.getExpiresAt());
    }

    private GeneratedPlaylistResponse buildCachedResponse (GeneratedPlaylist playlist) {
        List<Long> songIds = playlist.getSongs().stream().map(GeneratedPlaylistSong::getSongId).toList();

        return buildPlaylistResponse(playlist.getPlaylistType(), songIds, playlist.getGeneratedAt(), playlist.getExpiresAt());
    }

    private List<Long> generateSongIds (Long listenerId, Function<Long, List<Long>> songSupplier) {

        List<Long> songIds = songSupplier.apply(listenerId).stream().distinct().toList();

        return completePlaylist(listenerId, songIds, GENERATED_PLAYLIST_SIZE);
    }

    private GeneratedPlaylist savePlaylist(
            GeneratedPlaylist playlist,
            Long listenerId,
            GeneratedPlaylistType type,
            List<Long> songIds) {

        if (playlist == null) {
            playlist = new GeneratedPlaylist();
        }

        Instant now = Instant.now();

        playlist.setListenerId(listenerId);
        playlist.setPlaylistType(type);
        playlist.setGeneratedAt(now);
        playlist.setExpiresAt(now.plus(type.getCacheDuration()));

        playlist.getSongs().clear();

        for (int i = 0; i < songIds.size(); i++) {
            GeneratedPlaylistSong song = new GeneratedPlaylistSong();
            song.setSongId(songIds.get(i));
            song.setPosition(i + 1);
            playlist.addSong(song);
        }

        return generatedPlaylistRepository.save(playlist);
    }

    private GeneratedPlaylistResponse buildPlaylistResponse (GeneratedPlaylistType type, List<Long> songIds, Instant generatedAt, Instant expiresAt) {

        final Map<Long, SongResponse> songsById = catalogService.getSongsByIds(songIds);

        final List<SongResponse> songs = songIds.stream().map(songsById::get).filter(Objects::nonNull).toList();

        return new GeneratedPlaylistResponse(type, type.getDisplayName(), type.getDescription(), generatedAt, expiresAt, songs);
    }

    private GeneratedPlaylistSummaryResponse toGeneratedPlaylistSummary (GeneratedPlaylistType type, GeneratedPlaylist playlist) {
        return new GeneratedPlaylistSummaryResponse(
                type,
                type.getDisplayName(),
                type.getDescription(),
                playlist != null && !isExpired(playlist),
                playlist == null ? null : playlist.getGeneratedAt(),
                playlist == null ? null : playlist.getExpiresAt()
        );
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

        return findListenerSongs(
                listenerId,
                sql,
                Timestamp.from(inactivityCutoff),
                FORGOTTEN_GEMS_MIN_STREAMS,
                Timestamp.from(inactivityCutoff),
                GENERATED_PLAYLIST_SIZE
        );
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

        return findListenerSongs(
                listenerId,
                sql,
                Timestamp.from(lookbackStart),
                Timestamp.from(windowEnd),
                REDISCOVER_MIN_STREAMS,
                Timestamp.from(inactivityCutoff),
                GENERATED_PLAYLIST_SIZE
        );
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

    private PlaylistResponse findPlaylist (Long playlistId) {
        PlaylistSummaryResponse summary = jdbcTemplate.queryForObject("""
                SELECT
                    p.playlist_id,
                    p.playlist_name,
                    p.type::text AS type,
                    p.playlist_url,
                    p.picture_url,
                    p.creator_id,
                    creator.username AS creator_username,
                    p.created_at,
                    COUNT(DISTINCT pm.listener_id)::int AS member_count,
                    COUNT(DISTINCT ps.song_id)::int AS song_count
                FROM playlist p
                JOIN listener creator ON creator.listener_id = p.creator_id
                LEFT JOIN playlist_member pm ON pm.playlist_id = p.playlist_id
                LEFT JOIN playlist_song ps ON ps.playlist_id = p.playlist_id
                WHERE p.playlist_id = ?
                GROUP BY p.playlist_id, creator.username
                """, this::mapPlaylistSummary, playlistId);

        return new PlaylistResponse(summary.playlistId(), summary.name(), summary.type(), summary.playlistUrl(), summary.pictureUrl(), summary.creatorId(), summary.creatorUsername(), summary.createdAt(), summary.memberCount(), summary.songCount(), findPlaylistMembers(playlistId), findPlaylistSongs(playlistId));
    }

    private List<PlaylistMemberResponse> findPlaylistMembers (Long playlistId) {
        return jdbcTemplate.query("""
                SELECT
                    l.listener_id,
                    l.username,
                    pm.joined_at
                FROM playlist_member pm
                JOIN listener l ON l.listener_id = pm.listener_id
                WHERE pm.playlist_id = ?
                ORDER BY pm.joined_at, l.username
                """, this::mapPlaylistMember, playlistId);
    }

    private List<PlaylistSongResponse> findPlaylistSongs (Long playlistId) {
        return jdbcTemplate.query("""
                SELECT
                    s.song_id,
                    s.title,
                    p.performer_id AS main_performer_id,
                    p.nickname AS main_performer_name,
                    ps.added_by_listener_id,
                    ps.added_at,
                    COUNT(v.listener_id)::int AS vote_count
                FROM playlist_song ps
                JOIN song s ON s.song_id = ps.song_id
                JOIN performer p ON p.performer_id = s.main_performer_id
                LEFT JOIN playlist_song_vote v
                    ON v.playlist_id = ps.playlist_id
                   AND v.song_id = ps.song_id
                WHERE ps.playlist_id = ?
                GROUP BY s.song_id, p.performer_id, ps.added_by_listener_id, ps.added_at
                ORDER BY ps.added_at, s.title
                """, this::mapPlaylistSong, playlistId);
    }

    private PlaylistSummaryResponse mapPlaylistSummary (ResultSet rs, int rowNum) throws SQLException {
        return new PlaylistSummaryResponse(rs.getLong("playlist_id"), rs.getString("playlist_name"), rs.getString("type"), rs.getString("playlist_url"), rs.getString("picture_url"), rs.getLong("creator_id"), rs.getString("creator_username"), toInstant(rs, "created_at"), rs.getInt("member_count"), rs.getInt("song_count"));
    }

    private PlaylistMemberResponse mapPlaylistMember (ResultSet rs, int rowNum) throws SQLException {
        return new PlaylistMemberResponse(rs.getLong("listener_id"), rs.getString("username"), toInstant(rs, "joined_at"));
    }

    private PlaylistSongResponse mapPlaylistSong (ResultSet rs, int rowNum) throws SQLException {
        Long addedByListenerId = rs.getObject("added_by_listener_id", Long.class);
        return new PlaylistSongResponse(rs.getLong("song_id"), rs.getString("title"), rs.getLong("main_performer_id"), rs.getString("main_performer_name"), addedByListenerId, toInstant(rs, "added_at"), rs.getInt("vote_count"));
    }

    private Long requireListenerIdForExistingPlaylist (String username, Long playlistId) {
        final Long listenerId = requireListenerId(username);
        ensurePlaylistExists(playlistId);
        return listenerId;
    }

    private Long requireListenerId (String username) {
        return listenerRepository.findByUsername(username).map(Listener::getId).orElseThrow(() -> new NotFoundException("Listener not found: " + username));
    }

    private void ensurePlaylistExists (Long playlistId) {
        if (!playlistExists(playlistId)) {
            throw new NotFoundException("Playlist not found: " + playlistId);
        }
    }

    private boolean playlistExists (Long playlistId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM playlist
                    WHERE playlist_id = ?
                )
                """, Boolean.class, playlistId);
        return Boolean.TRUE.equals(exists);
    }

    private void requireSongExists (Long songId) {
        if (!songRepository.existsById(songId)) {
            throw new NotFoundException("Song not found: " + songId);
        }
    }

    private void requirePlaylistCreator (Long listenerId, Long playlistId) {
        if (!isPlaylistCreator(listenerId, playlistId)) {
            throw new AccessDeniedException("Only the playlist creator can change this playlist.");
        }
    }

    private boolean isPlaylistCreator (Long listenerId, Long playlistId) {
        Boolean creator = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM playlist
                    WHERE playlist_id = ?
                      AND creator_id = ?
                )
                """, Boolean.class, playlistId, listenerId);
        return Boolean.TRUE.equals(creator);
    }

    private void requirePlaylistMember (Long listenerId, Long playlistId) {
        Boolean member = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM playlist_member
                    WHERE listener_id = ?
                      AND playlist_id = ?
                )
                """, Boolean.class, listenerId, playlistId);

        if (!Boolean.TRUE.equals(member)) {
            throw new AccessDeniedException("Only playlist members can change playlist songs or votes.");
        }
    }

    private void requirePlaylistSong (Long playlistId, Long songId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM playlist_song
                    WHERE playlist_id = ?
                      AND song_id = ?
                )
                """, Boolean.class, playlistId, songId);

        if (!Boolean.TRUE.equals(exists)) {
            throw new NotFoundException("Song " + songId + " is not in playlist " + playlistId + ".");
        }
    }

    private String normalizeTextFilter (String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private String blankToNull (String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Instant toInstant (ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
