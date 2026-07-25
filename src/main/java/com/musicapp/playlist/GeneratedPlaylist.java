package com.musicapp.playlist;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "generated_playlist", uniqueConstraints = @UniqueConstraint(columnNames = {"listener_id", "playlist_type"}))
@Getter
@Setter
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
    private List<GeneratedPlaylistSong> songs = new ArrayList<>();

    public void addSong (GeneratedPlaylistSong song) {
        songs.add(song);
        song.setPlaylist(this);
    }

    public void clearSongs () {
        songs.clear();
    }
}