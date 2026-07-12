package com.musicapp;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.musicapp.common.GlobalExceptionHandler;
import com.musicapp.listener.ListenerActionController;
import com.musicapp.listener.ListenerActionService;
import com.musicapp.listener.ListenerAttitude;
import com.musicapp.listener.dto.PerformerActionResponse;
import com.musicapp.listener.dto.SongActionResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListenerActionControllerTest {
    private MockMvc mockMvc;
    private FakeListenerActionService listenerActionService;

    @BeforeEach
    void setUp () {
        listenerActionService = new FakeListenerActionService();

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ListenerActionController(listenerActionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()
                ))
                .addFilters(new AuthenticationRequiredFilter())
                .build();
    }

    @Test
    void listenerActionsRequireAuthentication () throws Exception {
        mockMvc.perform(post("/api/listener/me/songs/1/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void getSongStateReturnsResponseShape () throws Exception {
        listenerActionService.songResponse = songResponse(15, 2, ListenerAttitude.like, false);

        mockMvc.perform(get("/api/listener/me/songs/1").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listenerId").value(1))
                .andExpect(jsonPath("$.songId").value(1))
                .andExpect(jsonPath("$.streamCount").value(15))
                .andExpect(jsonPath("$.skipCount").value(2))
                .andExpect(jsonPath("$.attitude").value("like"))
                .andExpect(jsonPath("$.blocked").value(false));

        assertThat(listenerActionService.operation).isEqualTo("getSongState");
    }

    @Test
    void streamAndSkipSongsMapSkippedFlag () throws Exception {
        listenerActionService.songResponse = songResponse(16, 2, null, false);

        mockMvc.perform(post("/api/listener/me/songs/1/stream").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.streamCount").value(16));
        assertThat(listenerActionService.operation).isEqualTo("streamSong");
        assertThat(listenerActionService.skipped).isFalse();

        listenerActionService.songResponse = songResponse(17, 3, null, false);
        mockMvc.perform(post("/api/listener/me/songs/1/skip").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.skipCount").value(3));
        assertThat(listenerActionService.skipped).isTrue();
    }

    @Test
    void songAttitudesAcceptLikeDislikeAndNotInterested () throws Exception {
        assertSongAttitude("like", ListenerAttitude.like);
        assertSongAttitude("dislike", ListenerAttitude.dislike);
        assertSongAttitude("not_interested", ListenerAttitude.not_interested);
    }

    @Test
    void songAttitudeValidationErrorsUseErrorResponseShape () throws Exception {
        mockMvc.perform(put("/api/listener/me/songs/1/attitude")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attitude": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void clearSongAttitudeMapsToService () throws Exception {
        listenerActionService.songResponse = songResponse(15, 2, null, false);

        mockMvc.perform(delete("/api/listener/me/songs/1/attitude").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.attitude").doesNotExist());

        assertThat(listenerActionService.operation).isEqualTo("clearSongAttitude");
    }

    @Test
    void blockAndUnblockSongMapToServiceAndAreRepeatable () throws Exception {
        listenerActionService.songResponse = songResponse(15, 2, null, true);

        mockMvc.perform(put("/api/listener/me/songs/1/block").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        mockMvc.perform(put("/api/listener/me/songs/1/block").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));

        assertThat(listenerActionService.operation).isEqualTo("blockSong");
        assertThat(listenerActionService.callCount).isEqualTo(2);

        listenerActionService.songResponse = songResponse(15, 2, null, false);
        mockMvc.perform(delete("/api/listener/me/songs/1/block").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(false));
        assertThat(listenerActionService.operation).isEqualTo("unblockSong");
    }

    @Test
    void performerFollowAndUnfollowMapToService () throws Exception {
        listenerActionService.performerResponse = performerResponse(true, null, false);

        mockMvc.perform(put("/api/listener/me/performers/1/follow").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.following").value(true));
        assertThat(listenerActionService.operation).isEqualTo("followPerformer");

        listenerActionService.performerResponse = performerResponse(false, null, false);
        mockMvc.perform(delete("/api/listener/me/performers/1/follow").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.following").value(false));
        assertThat(listenerActionService.operation).isEqualTo("unfollowPerformer");
    }

    @Test
    void performerAttitudeAndClearMapToService () throws Exception {
        listenerActionService.performerResponse = performerResponse(false, ListenerAttitude.dislike, false);

        mockMvc.perform(put("/api/listener/me/performers/1/attitude")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attitude": "dislike"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attitude").value("dislike"));
        assertThat(listenerActionService.operation).isEqualTo("setPerformerAttitude");
        assertThat(listenerActionService.attitude).isEqualTo(ListenerAttitude.dislike);

        listenerActionService.performerResponse = performerResponse(false, null, false);
        mockMvc.perform(delete("/api/listener/me/performers/1/attitude").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.attitude").doesNotExist());
        assertThat(listenerActionService.operation).isEqualTo("clearPerformerAttitude");
    }

    @Test
    void blockAndUnblockPerformerMapToServiceAndAreRepeatable () throws Exception {
        listenerActionService.performerResponse = performerResponse(false, null, true);

        mockMvc.perform(put("/api/listener/me/performers/1/block").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        mockMvc.perform(put("/api/listener/me/performers/1/block").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));

        assertThat(listenerActionService.operation).isEqualTo("blockPerformer");
        assertThat(listenerActionService.callCount).isEqualTo(2);

        listenerActionService.performerResponse = performerResponse(false, null, false);
        mockMvc.perform(delete("/api/listener/me/performers/1/block").principal(authentication())).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(false));
        assertThat(listenerActionService.operation).isEqualTo("unblockPerformer");
    }

    private void assertSongAttitude (String jsonValue, ListenerAttitude expected) throws Exception {
        listenerActionService.songResponse = songResponse(15, 2, expected, false);

        mockMvc.perform(put("/api/listener/me/songs/1/attitude")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attitude": "%s"
                                }
                                """.formatted(jsonValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attitude").value(jsonValue));

        assertThat(listenerActionService.operation).isEqualTo("setSongAttitude");
        assertThat(listenerActionService.attitude).isEqualTo(expected);
    }

    private UsernamePasswordAuthenticationToken authentication () {
        return new UsernamePasswordAuthenticationToken("musiclover42", "password");
    }

    private SongActionResponse songResponse (int streamCount, int skipCount, ListenerAttitude attitude, boolean blocked) {
        return new SongActionResponse(
                1L,
                1L,
                streamCount,
                skipCount,
                attitude,
                blocked,
                blocked ? Instant.parse("2024-03-01T08:30:00Z") : null
        );
    }

    private PerformerActionResponse performerResponse (boolean following, ListenerAttitude attitude, boolean blocked) {
        return new PerformerActionResponse(
                1L,
                1L,
                following,
                attitude,
                blocked,
                following ? Instant.parse("2024-03-01T08:30:00Z") : null,
                blocked ? Instant.parse("2024-03-01T08:30:00Z") : null
        );
    }

    static class FakeListenerActionService extends ListenerActionService {
        private SongActionResponse songResponse;
        private PerformerActionResponse performerResponse;
        private String operation;
        private boolean skipped;
        private ListenerAttitude attitude;
        private int callCount;

        FakeListenerActionService () {
            super(null, null, null, null, null);
        }

        @Override
        public SongActionResponse getSongState (String username, Long songId) {
            operation = "getSongState";
            return songResponse;
        }

        @Override
        public SongActionResponse streamSong (String username, Long songId, boolean skipped) {
            operation = "streamSong";
            this.skipped = skipped;
            return songResponse;
        }

        @Override
        public SongActionResponse setSongAttitude (String username, Long songId, ListenerAttitude attitude) {
            operation = "setSongAttitude";
            this.attitude = attitude;
            return songResponse;
        }

        @Override
        public SongActionResponse clearSongAttitude (String username, Long songId) {
            operation = "clearSongAttitude";
            return songResponse;
        }

        @Override
        public SongActionResponse blockSong (String username, Long songId) {
            operation = "blockSong";
            callCount++;
            return songResponse;
        }

        @Override
        public SongActionResponse unblockSong (String username, Long songId) {
            operation = "unblockSong";
            return songResponse;
        }

        @Override
        public PerformerActionResponse followPerformer (String username, Long performerId) {
            operation = "followPerformer";
            return performerResponse;
        }

        @Override
        public PerformerActionResponse unfollowPerformer (String username, Long performerId) {
            operation = "unfollowPerformer";
            return performerResponse;
        }

        @Override
        public PerformerActionResponse setPerformerAttitude (String username, Long performerId, ListenerAttitude attitude) {
            operation = "setPerformerAttitude";
            this.attitude = attitude;
            return performerResponse;
        }

        @Override
        public PerformerActionResponse clearPerformerAttitude (String username, Long performerId) {
            operation = "clearPerformerAttitude";
            return performerResponse;
        }

        @Override
        public PerformerActionResponse blockPerformer (String username, Long performerId) {
            operation = "blockPerformer";
            callCount++;
            return performerResponse;
        }

        @Override
        public PerformerActionResponse unblockPerformer (String username, Long performerId) {
            operation = "unblockPerformer";
            return performerResponse;
        }
    }

    static class AuthenticationRequiredFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal (HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            if (request.getUserPrincipal() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
