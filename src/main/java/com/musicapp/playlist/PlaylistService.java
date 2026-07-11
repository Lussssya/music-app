package com.musicapp.playlist;

import com.musicapp.catalog.SongRepository;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.playlist.dto.CreatePlaylistRequest;
import com.musicapp.playlist.dto.PlaylistMemberResponse;
import com.musicapp.playlist.dto.PlaylistResponse;
import com.musicapp.playlist.dto.PlaylistSongResponse;
import com.musicapp.playlist.dto.PlaylistSummaryResponse;
import com.musicapp.playlist.dto.UpdatePlaylistRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlaylistService {
    private final ListenerRepository listenerRepository;
    private final SongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;

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
                """, this::mapPlaylistSummary, normalizeTextFilter(search), normalizeTextFilter(search),
                type == null ? null : type.dbValue(), type == null ? null : type.dbValue(),
                creatorId, creatorId, memberId, memberId);
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
                """, request.name().trim(), request.type().dbValue(), blankToNull(request.playlistUrl()),
                blankToNull(request.pictureUrl()), playlistId);

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

        return new PlaylistResponse(
                summary.playlistId(),
                summary.name(),
                summary.type(),
                summary.playlistUrl(),
                summary.pictureUrl(),
                summary.creatorId(),
                summary.creatorUsername(),
                summary.createdAt(),
                summary.memberCount(),
                summary.songCount(),
                findPlaylistMembers(playlistId),
                findPlaylistSongs(playlistId)
        );
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
        return new PlaylistSummaryResponse(
                rs.getLong("playlist_id"),
                rs.getString("playlist_name"),
                rs.getString("type"),
                rs.getString("playlist_url"),
                rs.getString("picture_url"),
                rs.getLong("creator_id"),
                rs.getString("creator_username"),
                toInstant(rs, "created_at"),
                rs.getInt("member_count"),
                rs.getInt("song_count")
        );
    }

    private PlaylistMemberResponse mapPlaylistMember (ResultSet rs, int rowNum) throws SQLException {
        return new PlaylistMemberResponse(
                rs.getLong("listener_id"),
                rs.getString("username"),
                toInstant(rs, "joined_at")
        );
    }

    private PlaylistSongResponse mapPlaylistSong (ResultSet rs, int rowNum) throws SQLException {
        Long addedByListenerId = rs.getObject("added_by_listener_id", Long.class);
        return new PlaylistSongResponse(
                rs.getLong("song_id"),
                rs.getString("title"),
                rs.getLong("main_performer_id"),
                rs.getString("main_performer_name"),
                addedByListenerId,
                toInstant(rs, "added_at"),
                rs.getInt("vote_count")
        );
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
