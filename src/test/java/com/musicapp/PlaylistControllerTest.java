package com.musicapp;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.musicapp.common.GlobalExceptionHandler;
import com.musicapp.playlist.PlaylistController;
import com.musicapp.playlist.PlaylistService;
import com.musicapp.playlist.PlaylistType;
import com.musicapp.playlist.dto.CreatePlaylistRequest;
import com.musicapp.playlist.dto.PlaylistMemberResponse;
import com.musicapp.playlist.dto.PlaylistResponse;
import com.musicapp.playlist.dto.PlaylistSongResponse;
import com.musicapp.playlist.dto.PlaylistSummaryResponse;
import com.musicapp.playlist.dto.UpdatePlaylistRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaylistControllerTest {
    private MockMvc mockMvc;
    private FakePlaylistService playlistService;

    @BeforeEach
    void setUp () {
        playlistService = new FakePlaylistService();

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PlaylistController(playlistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()
                ))
                .addFilters(new MutationsRequireAuthenticationFilter())
                .build();
    }

    @Test
    void findPlaylistsUsesSearchAndFilters () throws Exception {
        playlistService.summaries = List.of(summary());

        mockMvc.perform(get("/api/playlists?search=focus&type=public&creatorId=12&memberId=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playlistId").value(1))
                .andExpect(jsonPath("$[0].name").value("Focus Study"))
                .andExpect(jsonPath("$[0].type").value("public"))
                .andExpect(jsonPath("$[0].memberCount").value(3))
                .andExpect(jsonPath("$[0].songCount").value(2));

        assertThat(playlistService.search).isEqualTo("focus");
        Assertions.assertThat(playlistService.type).isEqualTo(PlaylistType.PUBLIC);
        assertThat(playlistService.creatorId).isEqualTo(12L);
        assertThat(playlistService.memberId).isEqualTo(5L);
    }

    @Test
    void getPlaylistReturnsDetails () throws Exception {
        playlistService.response = playlist();

        mockMvc.perform(get("/api/playlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1))
                .andExpect(jsonPath("$.members[0].username").value("musiclover42"))
                .andExpect(jsonPath("$.songs[0].title").value("Midnight Sun"))
                .andExpect(jsonPath("$.songs[0].voteCount").value(2));

        assertThat(playlistService.playlistId).isEqualTo(1L);
    }

    @Test
    void createPlaylistRequiresAuthentication () throws Exception {
        mockMvc.perform(post("/api/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Late Night Finds",
                                  "type": "private"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPlaylistReturnsCreatedPlaylist () throws Exception {
        playlistService.response = playlist();

        mockMvc.perform(post("/api/playlists")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Late Night Finds",
                                  "type": "private",
                                  "playlistUrl": "https://musicapp.com/playlist/late-night-finds",
                                  "pictureUrl": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playlistId").value(1))
                .andExpect(jsonPath("$.name").value("Late Night Finds"));

        assertThat(playlistService.username).isEqualTo("musiclover42");
        assertThat(playlistService.createRequest.name()).isEqualTo("Late Night Finds");
        assertThat(playlistService.createRequest.type()).isEqualTo(PlaylistType.PRIVATE);
    }

    @Test
    void createPlaylistValidationErrorsUseErrorResponseShape () throws Exception {
        mockMvc.perform(post("/api/playlists")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "type": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void updatePlaylistRequiresCreatorPermission () throws Exception {
        playlistService.exception = new AccessDeniedException("Only the playlist creator can change this playlist.");

        mockMvc.perform(put("/api/playlists/1")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "type": "shared"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messages[0]").value("Only the playlist creator can change this playlist."));
    }

    @Test
    void updatePlaylistMapsRequest () throws Exception {
        playlistService.response = playlist();

        mockMvc.perform(put("/api/playlists/1")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "type": "shared"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1));

        assertThat(playlistService.operation).isEqualTo("update");
        assertThat(playlistService.updateRequest.name()).isEqualTo("Updated");
        assertThat(playlistService.updateRequest.type()).isEqualTo(PlaylistType.SHARED);
    }

    @Test
    void deletePlaylistRequiresCreatorPermission () throws Exception {
        playlistService.exception = new AccessDeniedException("Only the playlist creator can change this playlist.");

        mockMvc.perform(delete("/api/playlists/1").principal(authentication())).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deletePlaylistReturnsNoContent () throws Exception {
        mockMvc.perform(delete("/api/playlists/1").principal(authentication())).andExpect(status().isNoContent());

        assertThat(playlistService.operation).isEqualTo("delete");
        assertThat(playlistService.playlistId).isEqualTo(1L);
    }

    @Test
    void joinAndLeavePlaylistMapToService () throws Exception {
        playlistService.response = playlist();

        mockMvc.perform(put("/api/playlists/1/members/me").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1));
        assertThat(playlistService.operation).isEqualTo("join");

        mockMvc.perform(delete("/api/playlists/1/members/me").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1));
        assertThat(playlistService.operation).isEqualTo("leave");
    }

    @Test
    void addRemoveVoteAndUnvoteSongsMapToService () throws Exception {
        playlistService.response = playlist();

        mockMvc.perform(put("/api/playlists/1/songs/3").principal(authentication())).andExpect(status().isOk());
        assertThat(playlistService.operation).isEqualTo("addSong");
        assertThat(playlistService.songId).isEqualTo(3L);

        mockMvc.perform(delete("/api/playlists/1/songs/3").principal(authentication())).andExpect(status().isOk());
        assertThat(playlistService.operation).isEqualTo("removeSong");

        mockMvc.perform(put("/api/playlists/1/songs/3/vote").principal(authentication())).andExpect(status().isOk());
        assertThat(playlistService.operation).isEqualTo("vote");

        mockMvc.perform(delete("/api/playlists/1/songs/3/vote").principal(authentication())).andExpect(status().isOk());
        assertThat(playlistService.operation).isEqualTo("unvote");
    }

    @Test
    void songChangesRequirePlaylistMembership () throws Exception {
        playlistService.exception = new AccessDeniedException("Only playlist members can change playlist songs or votes.");

        mockMvc.perform(put("/api/playlists/1/songs/3").principal(authentication())).andExpect(status().isForbidden()).andExpect(jsonPath("$.messages[0]").value("Only playlist members can change playlist songs or votes."));
    }

    private UsernamePasswordAuthenticationToken authentication () {
        return new UsernamePasswordAuthenticationToken("musiclover42", "password");
    }

    private PlaylistSummaryResponse summary () {
        return new PlaylistSummaryResponse(
                1L,
                "Focus Study",
                "public",
                "https://musicapp.com/playlist/focus-study",
                null,
                12L,
                "jazz_lover",
                Instant.parse("2024-03-01T08:30:00Z"),
                3,
                2
        );
    }

    private PlaylistResponse playlist () {
        return new PlaylistResponse(
                1L,
                "Late Night Finds",
                "private",
                "https://musicapp.com/playlist/late-night-finds",
                null,
                1L,
                "musiclover42",
                Instant.parse("2024-03-01T08:30:00Z"),
                1,
                1,
                List.of(new PlaylistMemberResponse(1L, "musiclover42", Instant.parse("2024-03-01T08:30:00Z"))),
                List.of(new PlaylistSongResponse(3L, "Midnight Sun", 1L, "Aurora Sky", 1L, Instant.parse("2024-03-01T08:30:00Z"), 2))
        );
    }

    static class FakePlaylistService extends PlaylistService {
        private List<PlaylistSummaryResponse> summaries = List.of();
        private PlaylistResponse response;
        private RuntimeException exception;
        private String username;
        private String search;
        private PlaylistType type;
        private Long creatorId;
        private Long memberId;
        private Long playlistId;
        private Long songId;
        private String operation;
        private CreatePlaylistRequest createRequest;
        private UpdatePlaylistRequest updateRequest;

        FakePlaylistService () {
            super(null, null, null);
        }

        @Override
        public List<PlaylistSummaryResponse> findPlaylists (String search, PlaylistType type, Long creatorId, Long memberId) {
            this.search = search;
            this.type = type;
            this.creatorId = creatorId;
            this.memberId = memberId;
            return summaries;
        }

        @Override
        public PlaylistResponse getPlaylist (Long playlistId) {
            this.playlistId = playlistId;
            return response;
        }

        @Override
        public PlaylistResponse createPlaylist (String username, CreatePlaylistRequest request) {
            this.username = username;
            this.createRequest = request;
            return response;
        }

        @Override
        public PlaylistResponse updatePlaylist (String username, Long playlistId, UpdatePlaylistRequest request) {
            throwIfNeeded();
            this.operation = "update";
            this.username = username;
            this.playlistId = playlistId;
            this.updateRequest = request;
            return response;
        }

        @Override
        public void deletePlaylist (String username, Long playlistId) {
            throwIfNeeded();
            this.operation = "delete";
            this.username = username;
            this.playlistId = playlistId;
        }

        @Override
        public PlaylistResponse joinPlaylist (String username, Long playlistId) {
            this.operation = "join";
            this.username = username;
            this.playlistId = playlistId;
            return response;
        }

        @Override
        public PlaylistResponse leavePlaylist (String username, Long playlistId) {
            this.operation = "leave";
            this.username = username;
            this.playlistId = playlistId;
            return response;
        }

        @Override
        public PlaylistResponse addSong (String username, Long playlistId, Long songId) {
            throwIfNeeded();
            this.operation = "addSong";
            this.username = username;
            this.playlistId = playlistId;
            this.songId = songId;
            return response;
        }

        @Override
        public PlaylistResponse removeSong (String username, Long playlistId, Long songId) {
            this.operation = "removeSong";
            this.username = username;
            this.playlistId = playlistId;
            this.songId = songId;
            return response;
        }

        @Override
        public PlaylistResponse voteForSong (String username, Long playlistId, Long songId) {
            this.operation = "vote";
            this.username = username;
            this.playlistId = playlistId;
            this.songId = songId;
            return response;
        }

        @Override
        public PlaylistResponse removeSongVote (String username, Long playlistId, Long songId) {
            this.operation = "unvote";
            this.username = username;
            this.playlistId = playlistId;
            this.songId = songId;
            return response;
        }

        private void throwIfNeeded () {
            if (exception != null) {
                throw exception;
            }
        }
    }

    static class MutationsRequireAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal (HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            if (!request.getMethod().equals("GET") && request.getUserPrincipal() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
