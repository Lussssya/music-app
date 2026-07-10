package com.musicapp.recommendation;

import com.musicapp.common.BadRequestException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.recommendation.dto.RecommendationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    private static final String USERNAME = "musiclover42";
    private static final long LISTENER_ID = 1L;

    @Mock
    private ListenerRepository listenerRepository;

    @Mock
    private JdbcOperations jdbcTemplate;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<Object[]> argumentCaptor;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void recommendationsAreRankedByScore () {
        final Listener listener = listenerWithId(LISTENER_ID);
        when(listenerRepository.findByUsername(USERNAME)).thenReturn(Optional.of(listener));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        recommendationService.getRecommendations(USERNAME, 10);

        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argumentCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("ORDER BY lr.recommendation_score DESC")
                .contains("lr.generated_at DESC")
                .contains("LIMIT ?");
        assertThat(argumentCaptor.getValue()).containsExactly(LISTENER_ID, 10);
    }

    @Test
    void recommendationLimitIsCapped () {
        final Listener listener = listenerWithId(LISTENER_ID);
        when(listenerRepository.findByUsername(USERNAME)).thenReturn(Optional.of(listener));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        recommendationService.getRecommendations(USERNAME, 150);

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).containsExactly(LISTENER_ID, 100);
    }

    @Test
    void recommendationLimitMustBePositive () {
        final Listener listener = listenerWithId(LISTENER_ID);
        when(listenerRepository.findByUsername(USERNAME)).thenReturn(Optional.of(listener));

        assertThatThrownBy(() -> recommendationService.getRecommendations(USERNAME, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Recommendation limit must be at least 1.");

        verifyNoInteractions(jdbcTemplate);
    }

    private Listener listenerWithId (Long listenerId) {
        final Listener listener = Listener.register(
                USERNAME,
                "musiclover42@example.com",
                "password-hash",
                "Male",
                java.time.LocalDate.of(1995, 3, 15),
                "United States"
        );
        ReflectionTestUtils.setField(listener, "id", listenerId);
        return listener;
    }
}
