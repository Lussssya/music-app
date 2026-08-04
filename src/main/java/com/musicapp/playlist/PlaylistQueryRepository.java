package com.musicapp.playlist;

import com.musicapp.playlist.dto.PlaylistMemberResponse;
import com.musicapp.playlist.dto.PlaylistResponse;
import com.musicapp.playlist.dto.PlaylistSongResponse;
import com.musicapp.playlist.dto.PlaylistSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlaylistQueryRepository {
    private final JdbcTemplate jdbcTemplate;

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

    private PlaylistSummaryResponse mapPlaylistSummary (ResultSet rs, int rowNum) throws SQLException {
        return new PlaylistSummaryResponse(rs.getLong("playlist_id"), rs.getString("playlist_name"), rs.getString("type"), rs.getString("playlist_url"), rs.getString("picture_url"), rs.getLong("creator_id"), rs.getString("creator_username"), toInstant(rs, "created_at"), rs.getInt("member_count"), rs.getInt("song_count"));
    }

    private Instant toInstant (ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeTextFilter (String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    public PlaylistResponse findPlaylist (Long playlistId) {
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

    private PlaylistMemberResponse mapPlaylistMember (ResultSet rs, int rowNum) throws SQLException {
        return new PlaylistMemberResponse(rs.getLong("listener_id"), rs.getString("username"), toInstant(rs, "joined_at"));
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

    private PlaylistSongResponse mapPlaylistSong (ResultSet rs, int rowNum) throws SQLException {
        Long addedByListenerId = rs.getObject("added_by_listener_id", Long.class);
        return new PlaylistSongResponse(rs.getLong("song_id"), rs.getString("title"), rs.getLong("main_performer_id"), rs.getString("main_performer_name"), addedByListenerId, toInstant(rs, "added_at"), rs.getInt("vote_count"));
    }



}
