package com.musicapp;

import com.musicapp.catalog.CatalogService;
import com.musicapp.catalog.PerformerRepository;
import com.musicapp.catalog.SongRepository;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerActionService;
import com.musicapp.listener.ListenerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListenerActionServiceTest {
    @Test
    void getFavoriteSongsPreservesQueryOrder () {
        final ListenerRepository listenerRepository = mock(ListenerRepository.class);
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final CatalogService catalogService = mock(CatalogService.class);
        final Listener listener = Listener.register(
                "musiclover42",
                "musiclover42@example.com",
                "password",
                "other",
                LocalDate.of(2000, 1, 1),
                "Armenia"
        );
        listener.setId(1L);

        final SongResponse first = songResponse(3L, "First");
        final SongResponse second = songResponse(1L, "Second");
        final SongResponse third = songResponse(2L, "Third");
        final List<Long> orderedSongIds = List.of(3L, 1L, 2L);

        when(listenerRepository.findByUsername("musiclover42")).thenReturn(Optional.of(listener));
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(orderedSongIds);
        when(catalogService.getSongsByIds(orderedSongIds)).thenReturn(Map.of(
                1L, second,
                2L, third,
                3L, first
        ));

        final ListenerActionService service = new ListenerActionService(
                listenerRepository,
                mock(SongRepository.class),
                mock(PerformerRepository.class),
                jdbcTemplate,
                catalogService
        );

        assertThat(service.getFavoriteSongs("musiclover42")).containsExactly(first, second, third);
    }

    private SongResponse songResponse (Long songId, String title) {
        return new SongResponse(songId, title, null, null, null, null, null, null, List.of());
    }
}
