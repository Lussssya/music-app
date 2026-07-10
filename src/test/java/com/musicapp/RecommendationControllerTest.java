package com.musicapp;

import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.GlobalExceptionHandler;
import com.musicapp.recommendation.RecommendationController;
import com.musicapp.recommendation.RecommendationService;
import com.musicapp.recommendation.dto.RecommendationResponse;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationControllerTest {
    private MockMvc mockMvc;
    private FakeRecommendationService recommendationService;

    @BeforeEach
    void setUp () {
        recommendationService = new FakeRecommendationService();
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationController(recommendationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .addFilters(new AuthenticationRequiredFilter())
                .build();
    }

    @Test
    void recommendationsRequireAuthentication () throws Exception {
        mockMvc.perform(get("/api/recommendations")).andExpect(status().isUnauthorized());
    }

    @Test
    void rebuildRecommendationsRequireAuthentication () throws Exception {
        mockMvc.perform(post("/api/recommendations/rebuild")).andExpect(status().isUnauthorized());
    }

    @Test
    void recommendationsReturnResponseShape () throws Exception {
        recommendationService.response = List.of(recommendation());

        mockMvc.perform(get("/api/recommendations?limit=3").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(162.5))
                .andExpect(jsonPath("$[0].generatedAt").value("2024-03-01T08:30:00Z"))
                .andExpect(jsonPath("$[0].song.songId").value(3))
                .andExpect(jsonPath("$[0].song.title").value("Midnight Sun"))
                .andExpect(jsonPath("$[0].song.mainPerformer.performerId").value(1))
                .andExpect(jsonPath("$[0].song.mainPerformer.nickname").value("Aurora Sky"))
                .andExpect(jsonPath("$[0].song.album.albumId").value(2))
                .andExpect(jsonPath("$[0].song.genres[0]").value("Electronic"))
                .andExpect(jsonPath("$[0].song.genres[1]").value("Pop"));

        assertThat(recommendationService.username).isEqualTo("musiclover42");
        assertThat(recommendationService.limit).isEqualTo(3);
    }

    @Test
    void recommendationsUseDefaultLimit () throws Exception {
        recommendationService.response = List.of();

        mockMvc.perform(get("/api/recommendations").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        assertThat(recommendationService.username).isEqualTo("musiclover42");
        assertThat(recommendationService.limit).isEqualTo(20);
    }

    @Test
    void rebuildRecommendationsReturnResponseShape () throws Exception {
        recommendationService.rebuildResponse = List.of(recommendation());

        mockMvc.perform(post("/api/recommendations/rebuild?limit=2").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(162.5))
                .andExpect(jsonPath("$[0].song.songId").value(3))
                .andExpect(jsonPath("$[0].song.title").value("Midnight Sun"));

        assertThat(recommendationService.rebuildUsername).isEqualTo("musiclover42");
        assertThat(recommendationService.rebuildLimit).isEqualTo(2);
    }

    @Test
    void recommendationBadRequestUsesErrorResponseShape () throws Exception {
        recommendationService.exception = new BadRequestException("Recommendation limit must be at least 1.");

        mockMvc.perform(get("/api/recommendations?limit=0").principal(authentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.messages[0]").value("Recommendation limit must be at least 1."));
    }

    private UsernamePasswordAuthenticationToken authentication () {
        return new UsernamePasswordAuthenticationToken("musiclover42", "password");
    }

    private RecommendationResponse recommendation () {
        return new RecommendationResponse(
                new BigDecimal("162.5000"),
                Instant.parse("2024-03-01T08:30:00Z"),
                new SongResponse(
                        3L,
                        "Midnight Sun",
                        "https://musicapp.com/songs/3",
                        LocalDate.of(2024, 1, 20),
                        "Written by Aurora Sky",
                        new BigDecimal("0.0035"),
                        new PerformerSummary(1L, "Aurora Sky"),
                        new AlbumSummary(2L, "Electric Dreams", LocalDate.of(2024, 1, 20)),
                        List.of("Electronic", "Pop")
                )
        );
    }

    private static class FakeRecommendationService extends RecommendationService {
        private List<RecommendationResponse> response = List.of();
        private List<RecommendationResponse> rebuildResponse = List.of();
        private RuntimeException exception;
        private String username;
        private int limit;
        private String rebuildUsername;
        private int rebuildLimit;

        FakeRecommendationService () {
            super(null, null);
        }

        @Override
        public List<RecommendationResponse> getRecommendations (String username, int limit) {
            this.username = username;
            this.limit = limit;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        @Override
        public List<RecommendationResponse> rebuildRecommendations (String username, int limit) {
            this.rebuildUsername = username;
            this.rebuildLimit = limit;
            if (exception != null) {
                throw exception;
            }
            return rebuildResponse;
        }
    }

    private static class AuthenticationRequiredFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal (
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            if (request.getUserPrincipal() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
