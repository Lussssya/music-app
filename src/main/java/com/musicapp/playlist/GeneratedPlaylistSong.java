package com.musicapp.playlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "generated_playlist_song")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedPlaylistSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generated_playlist_song_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_playlist_id", nullable = false)
    private GeneratedPlaylist playlist;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(nullable = false)
    private int position;

    GeneratedPlaylistSong (GeneratedPlaylist playlist, Long songId, int position) {
        this.playlist = Objects.requireNonNull(playlist, "playlist should not be null");
        this.songId = Objects.requireNonNull(songId, "songId should not be null");

        if (position <= 0) {
            throw new IllegalArgumentException("position should be greater than zero");
        }

        this.position = position;
    }
}