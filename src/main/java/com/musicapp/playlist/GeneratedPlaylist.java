package com.musicapp.playlist;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "generated_playlist", uniqueConstraints = @UniqueConstraint(columnNames = {"listener_id", "playlist_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedPlaylist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generated_playlist_id")
    private Long id;

    @Column(name = "listener_id", nullable = false)
    private Long listenerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "playlist_type", nullable = false, length = 64)
    private GeneratedPlaylistType playlistType;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private final List<GeneratedPlaylistSong> songs = new ArrayList<>();

    public void refresh (Long listenerId, GeneratedPlaylistType playlistType, Instant generatedAt, List<Long> songIds) {
        this.listenerId = Objects.requireNonNull(listenerId, "listenerId should not be null");
        this.playlistType = Objects.requireNonNull(playlistType, "playlistType should not be null");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt should not be null");
        this.expiresAt = generatedAt.plus(playlistType.getCacheDuration());

        replaceSongs(songIds);
    }

    public List<GeneratedPlaylistSong> getSongs () {
        return Collections.unmodifiableList(songs);
    }

    private void replaceSongs (List<Long> songIds) {
        Objects.requireNonNull(songIds, "songIds must not be null");
        songs.clear();

        for (int i = 0; i < songIds.size(); i++) {
            addSong(songIds.get(i), i + 1);
        }
    }

    private void addSong (Long songId, int position) {
        final GeneratedPlaylistSong song = new GeneratedPlaylistSong(this, songId, position);
        songs.add(song);
    }
}