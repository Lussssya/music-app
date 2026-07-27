package com.musicapp;

import com.musicapp.catalog.DiscoveryController;
import com.musicapp.catalog.DiscoveryService;
import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SearchSuggestionResponse;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.catalog.dto.TrendingSongResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiscoveryControllerTest {
    private MockMvc mockMvc;
    private FakeDiscoveryService discoveryService;

    @BeforeEach
    void setUp () {
        discoveryService = new FakeDiscoveryService();
        mockMvc = MockMvcBuilders.standaloneSetup(new DiscoveryController(discoveryService)).build();
    }

    @Test
    void trendingSongsReturnRankingSignals () throws Exception {
        discoveryService.trending = new PageImpl<>(
                List.of(new TrendingSongResponse(song(), 42, 12)),
                PageRequest.of(1, 10),
                13
        );

        mockMvc.perform(get("/api/discovery/trending?page=1&size=10&days=14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].song.songId").value(3))
                .andExpect(jsonPath("$.content[0].streamCount").value(42))
                .andExpect(jsonPath("$.content[0].listenerCount").value(12))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(10));

        assertThat(discoveryService.page).isEqualTo(1);
        assertThat(discoveryService.size).isEqualTo(10);
        assertThat(discoveryService.days).isEqualTo(14);
    }

    @Test
    void suggestionsReturnTypedTargets () throws Exception {
        discoveryService.suggestions = List.of(
                new SearchSuggestionResponse("performer", 1L, "Aurora Sky", "Solo artist"),
                new SearchSuggestionResponse("album", 2L, "Electric Dreams", "Aurora Sky · Album")
        );

        mockMvc.perform(get("/api/discovery/suggestions?query=aur&limit=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("performer"))
                .andExpect(jsonPath("$[0].entityId").value(1))
                .andExpect(jsonPath("$[1].type").value("album"));

        assertThat(discoveryService.query).isEqualTo("aur");
        assertThat(discoveryService.limit).isEqualTo(6);
    }

    @Test
    void followedReleaseFeedUsesAuthenticatedUsername () throws Exception {
        discoveryService.followed = new PageImpl<>(List.of(song()), PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/api/discovery/following/releases")
                        .principal(new UsernamePasswordAuthenticationToken("musiclover42", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].songId").value(3));

        assertThat(discoveryService.username).isEqualTo("musiclover42");
    }

    private SongResponse song () {
        return new SongResponse(
                3L,
                "Midnight Sun",
                "https://musicapp.com/songs/3",
                LocalDate.of(2024, 1, 20),
                "Written by Aurora Sky",
                new BigDecimal("0.0035"),
                new PerformerSummary(1L, "Aurora Sky"),
                new AlbumSummary(2L, "Electric Dreams", LocalDate.of(2024, 1, 20)),
                List.of("Electronic", "Pop")
        );
    }

    private static class FakeDiscoveryService extends DiscoveryService {
        private Page<TrendingSongResponse> trending = Page.empty();
        private List<SearchSuggestionResponse> suggestions = List.of();
        private Page<SongResponse> followed = Page.empty();
        private int page;
        private int size;
        private int days;
        private String query;
        private int limit;
        private String username;

        FakeDiscoveryService () {
            super(null, null, null);
        }

        @Override
        public Page<TrendingSongResponse> getTrendingSongs (int page, int size, int days) {
            this.page = page;
            this.size = size;
            this.days = days;
            return trending;
        }

        @Override
        public List<SearchSuggestionResponse> getSearchSuggestions (String query, int limit) {
            this.query = query;
            this.limit = limit;
            return suggestions;
        }

        @Override
        public Page<SongResponse> getFollowedPerformerReleases (String username, int page, int size) {
            this.username = username;
            this.page = page;
            this.size = size;
            return followed;
        }
    }
}
