package com.musicapp.playlist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

@Entity
@Table(name = "generated_playlist_song")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedPlaylistSong {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generated_playlist_song_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_playlist_id", nullable = false)
    private GeneratedPlaylist playlist;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(nullable = false)
    private int position;
}