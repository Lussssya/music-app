package com.musicapp.playlist;

import com.musicapp.catalog.CatalogService;
import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.playlist.dto.GeneratedPlaylistResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeneratedPlaylistCacheTest {
    @Test
    void returnsAnUnexpiredCachedPlaylistInItsSavedOrder () {
        ListenerRepository listeners = mock(ListenerRepository.class);
        GeneratedPlaylistRepository generatedPlaylists = mock(GeneratedPlaylistRepository.class);
        CatalogService catalog = mock(CatalogService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Listener listener = mock(Listener.class);
        GeneratedPlaylist cachedPlaylist = cachedPlaylist();

        when(listener.getId()).thenReturn(7L);
        when(listeners.findByUsername("musiclover42")).thenReturn(Optional.of(listener));
        when(generatedPlaylists.findByListenerIdAndPlaylistType(7L, GeneratedPlaylistType.DAILY_REWIND)).thenReturn(Optional.of(cachedPlaylist));

        SongResponse firstSong = song(12L, "First");
        SongResponse secondSong = song(4L, "Second");
        when(catalog.getSongsByIds(List.of(12L, 4L))).thenReturn(Map.of(12L, firstSong, 4L, secondSong));

        PlaylistService service = new PlaylistService(listeners, null, jdbcTemplate, catalog, generatedPlaylists);
        GeneratedPlaylistResponse response = service.generatePlaylist("musiclover42", GeneratedPlaylistType.DAILY_REWIND);

        assertThat(response.songs()).containsExactly(firstSong, secondSong);
        assertThat(response.generatedAt()).isEqualTo(cachedPlaylist.getGeneratedAt());
        assertThat(response.expiresAt()).isEqualTo(cachedPlaylist.getExpiresAt());
        verify(generatedPlaylists, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void usesShortCacheWindowsForPlaylistsAffectedByRecentListening () {
        assertThat(GeneratedPlaylistType.DAILY_REWIND.getCacheDuration()).isEqualTo(Duration.ofDays(1));
        assertThat(GeneratedPlaylistType.WEEKLY_REWIND.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.ALL_TIME_REWIND.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.FORGOTTEN_GEMS.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.COMFORT_SONGS.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.NO_SKIPS.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.HIDDEN_FAVOURITES.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.GENRE_MIX.getCacheDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(GeneratedPlaylistType.REDISCOVER.getCacheDuration()).isEqualTo(Duration.ofDays(7));
    }

    private GeneratedPlaylist cachedPlaylist () {
        GeneratedPlaylist playlist = new GeneratedPlaylist();
        playlist.setPlaylistType(GeneratedPlaylistType.DAILY_REWIND);
        playlist.setGeneratedAt(Instant.parse("2026-07-25T10:00:00Z"));
        playlist.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

        playlist.addSong(cachedSong(12L, 1));
        playlist.addSong(cachedSong(4L, 2));
        return playlist;
    }

    private GeneratedPlaylistSong cachedSong (Long songId, int position) {
        GeneratedPlaylistSong song = new GeneratedPlaylistSong();
        song.setSongId(songId);
        song.setPosition(position);
        return song;
    }

    private SongResponse song (Long id, String title) {
        return new SongResponse(
                id, title, null, LocalDate.of(2026, 1, 1), null, BigDecimal.ZERO,
                new PerformerSummary(1L, "Artist"), new AlbumSummary(1L, "Album", LocalDate.of(2026, 1, 1)), List.of("Pop")
        );
    }
}
