package com.musicapp;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.GlobalExceptionHandler;
import com.musicapp.playlist.GeneratedPlaylistService;
import com.musicapp.playlist.GeneratedPlaylistType;
import com.musicapp.playlist.PlaylistController;
import com.musicapp.playlist.PlaylistService;
import com.musicapp.playlist.PlaylistType;
import com.musicapp.playlist.dto.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaylistControllerTest {
    private MockMvc mockMvc;
    private PlaylistService playlistService;
    private GeneratedPlaylistService generatedPlaylistService;

    @BeforeEach
    void setUp () {
        playlistService = mock(PlaylistService.class);
        generatedPlaylistService = mock(GeneratedPlaylistService.class);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PlaylistController(playlistService, generatedPlaylistService))
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
        when(playlistService.findPlaylists("focus", PlaylistType.PUBLIC, 12L, 5L)).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/playlists?search=focus&type=public&creatorId=12&memberId=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playlistId").value(1))
                .andExpect(jsonPath("$[0].name").value("Focus Study"))
                .andExpect(jsonPath("$[0].type").value("public"))
                .andExpect(jsonPath("$[0].memberCount").value(3))
                .andExpect(jsonPath("$[0].songCount").value(2));

        verify(playlistService).findPlaylists("focus", PlaylistType.PUBLIC, 12L, 5L);
    }

    @Test
    void getPlaylistReturnsDetails () throws Exception {
        when(playlistService.getPlaylist(1L)).thenReturn(playlist());

        mockMvc.perform(get("/api/playlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1))
                .andExpect(jsonPath("$.members[0].username").value("musiclover42"))
                .andExpect(jsonPath("$.songs[0].title").value("Midnight Sun"))
                .andExpect(jsonPath("$.songs[0].voteCount").value(2));

        verify(playlistService).getPlaylist(1L);
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
        when(playlistService.createPlaylist(eq("musiclover42"), any(CreatePlaylistRequest.class))).thenReturn(playlist());

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

        verify(playlistService).createPlaylist("musiclover42", new CreatePlaylistRequest(
                "Late Night Finds", PlaylistType.PRIVATE,
                "https://musicapp.com/playlist/late-night-finds", null));
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
        doThrow(new AccessDeniedException("Only the playlist creator can change this playlist."))
                .when(playlistService).updatePlaylist(eq("musiclover42"), eq(1L), any(UpdatePlaylistRequest.class));

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
        when(playlistService.updatePlaylist(eq("musiclover42"), eq(1L), any(UpdatePlaylistRequest.class))).thenReturn(playlist());

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

        verify(playlistService).updatePlaylist("musiclover42", 1L,
                new UpdatePlaylistRequest("Updated", PlaylistType.SHARED, null, null));
    }

    @Test
    void deletePlaylistRequiresCreatorPermission () throws Exception {
        doThrow(new AccessDeniedException("Only the playlist creator can change this playlist."))
                .when(playlistService).deletePlaylist("musiclover42", 1L);

        mockMvc.perform(delete("/api/playlists/1").principal(authentication())).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deletePlaylistReturnsNoContent () throws Exception {
        mockMvc.perform(delete("/api/playlists/1").principal(authentication())).andExpect(status().isNoContent());

        verify(playlistService).deletePlaylist("musiclover42", 1L);
    }

    @Test
    void joinAndLeavePlaylistMapToService () throws Exception {
        when(playlistService.joinPlaylist("musiclover42", 1L)).thenReturn(playlist());
        when(playlistService.leavePlaylist("musiclover42", 1L)).thenReturn(playlist());

        mockMvc.perform(put("/api/playlists/1/members/me").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1));
        verify(playlistService).joinPlaylist("musiclover42", 1L);

        mockMvc.perform(delete("/api/playlists/1/members/me").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value(1));
        verify(playlistService).leavePlaylist("musiclover42", 1L);
    }

    @Test
    void addRemoveVoteAndUnvoteSongsMapToService () throws Exception {
        when(playlistService.addSong("musiclover42", 1L, 3L)).thenReturn(playlist());
        when(playlistService.removeSong("musiclover42", 1L, 3L)).thenReturn(playlist());
        when(playlistService.voteForSong("musiclover42", 1L, 3L)).thenReturn(playlist());
        when(playlistService.removeSongVote("musiclover42", 1L, 3L)).thenReturn(playlist());

        mockMvc.perform(put("/api/playlists/1/songs/3").principal(authentication())).andExpect(status().isOk());
        verify(playlistService).addSong("musiclover42", 1L, 3L);

        mockMvc.perform(delete("/api/playlists/1/songs/3").principal(authentication())).andExpect(status().isOk());
        verify(playlistService).removeSong("musiclover42", 1L, 3L);

        mockMvc.perform(put("/api/playlists/1/songs/3/vote").principal(authentication())).andExpect(status().isOk());
        verify(playlistService).voteForSong("musiclover42", 1L, 3L);

        mockMvc.perform(delete("/api/playlists/1/songs/3/vote").principal(authentication())).andExpect(status().isOk());
        verify(playlistService).removeSongVote("musiclover42", 1L, 3L);
    }

    @Test
    void songChangesRequirePlaylistMembership () throws Exception {
        doThrow(new AccessDeniedException("Only playlist members can change playlist songs or votes."))
                .when(playlistService).addSong("musiclover42", 1L, 3L);

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

    @Test
    void getGeneratedPlaylistsReturnsAvailablePlaylists() throws Exception {
        when(generatedPlaylistService.getAvailableGeneratedPlaylists("musiclover42")).thenReturn(List.of(
                new GeneratedPlaylistSummaryResponse(
                        GeneratedPlaylistType.DAILY_REWIND,
                        "Daily Rewind",
                        "Your most played songs today",
                        true,
                        Instant.parse("2024-03-01T08:30:00Z"),
                        Instant.parse("2024-03-02T08:30:00Z")
                )
        ));

        mockMvc.perform(get("/api/playlists/generated").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DAILY_REWIND"))
                .andExpect(jsonPath("$[0].name").value("Daily Rewind"))
                .andExpect(jsonPath("$[0].description").value("Your most played songs today"))
                .andExpect(jsonPath("$[0].available").value(true));

        verify(generatedPlaylistService).getAvailableGeneratedPlaylists("musiclover42");
    }

    @Test
    void generatePlaylistUsesTypeAndAuthentication() throws Exception {
        when(generatedPlaylistService.generatePlaylist("musiclover42", GeneratedPlaylistType.DAILY_REWIND)).thenReturn(generatedPlaylist());

        mockMvc.perform(get("/api/playlists/generated/DAILY_REWIND")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DAILY_REWIND"))
                .andExpect(jsonPath("$.name").value("Daily Rewind"))
                .andExpect(jsonPath("$.songs[0].title").value("Midnight Sun"));

        verify(generatedPlaylistService).generatePlaylist("musiclover42", GeneratedPlaylistType.DAILY_REWIND);
    }

    @Test
    void generatePlaylistRejectsUnknownType() throws Exception {
        mockMvc.perform(get("/api/playlists/generated/UNKNOWN").principal(authentication())).andExpect(status().isBadRequest());
    }

    private GeneratedPlaylistResponse generatedPlaylist() {
        return new GeneratedPlaylistResponse(
                GeneratedPlaylistType.DAILY_REWIND,
                "Daily Rewind",
                "Your most played songs today",
                Instant.parse("2024-03-01T08:30:00Z"),
                Instant.parse("2024-03-02T08:30:00Z"),
                List.of(song())
        );
    }

    private SongResponse song () {
        return new SongResponse(
                3L, "Midnight Sun", "https://musicapp.com/songs/3", LocalDate.of(2024, 1, 20),
                "Written by Aurora Sky", new BigDecimal("0.0035"), new PerformerSummary(1L, "Aurora Sky"),
                new AlbumSummary(2L, "Electric Dreams", LocalDate.of(2024, 1, 20)), List.of("Electronic", "Pop")
        );
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
