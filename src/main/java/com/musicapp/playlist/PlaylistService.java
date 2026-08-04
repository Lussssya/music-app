package com.musicapp.playlist;

import com.musicapp.catalog.SongRepository;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.playlist.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlaylistService {
    private final PlaylistQueryRepository playlistQueryRepository;
    private final ListenerRepository listenerRepository;
    private final SongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<PlaylistSummaryResponse> findPlaylists (String search, PlaylistType type, Long creatorId, Long memberId) {
        return playlistQueryRepository.findPlaylists(search, type, creatorId, memberId);
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

    private PlaylistResponse findPlaylist (Long playlistId) {
        return playlistQueryRepository.findPlaylist(playlistId);
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
        final Boolean exists = jdbcTemplate.queryForObject("""
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
        final Boolean creator = jdbcTemplate.queryForObject("""
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
        final Boolean member = jdbcTemplate.queryForObject("""
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
        final Boolean exists = jdbcTemplate.queryForObject("""
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

    private String blankToNull (String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
