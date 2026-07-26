package com.musicapp;

import com.musicapp.common.BadRequestException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import com.musicapp.recommendation.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationServiceTest {
    private static final String USERNAME = "musiclover42";
    private static final long LISTENER_ID = 1L;

    @Test
    void recommendationsAreRankedByScore () {
        final CapturingJdbcOperations jdbcOperations = new CapturingJdbcOperations();
        final RecommendationService recommendationService = new RecommendationService(listenerRepository(), jdbcOperations.proxy());

        recommendationService.getRecommendations(USERNAME, 0, 10);

        assertThat(jdbcOperations.sql)
                .contains("ORDER BY lr.recommendation_score DESC")
                .contains("lr.generated_at DESC")
                .contains("LIMIT ?");
        assertThat(jdbcOperations.arguments).containsExactly(LISTENER_ID, 10, 0);
    }

    @Test
    void recommendationPageSizeCannotExceedMaximum () {
        CapturingJdbcOperations jdbcOperations = new CapturingJdbcOperations();
        RecommendationService recommendationService = new RecommendationService(
                listenerRepository(),
                jdbcOperations.proxy()
        );

        assertThatThrownBy(() -> recommendationService.getRecommendations(USERNAME, 0, 26))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Illegal recommendation page or size.");
        assertThat(jdbcOperations.queryCount).isZero();
    }

    @Test
    void recommendationPageAndSizeMustBeValid () {
        final CapturingJdbcOperations jdbcOperations = new CapturingJdbcOperations();
        final RecommendationService recommendationService = new RecommendationService(listenerRepository(), jdbcOperations.proxy());

        assertThatThrownBy(() -> recommendationService.getRecommendations(USERNAME, -1, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Illegal recommendation page or size.");
        assertThatThrownBy(() -> recommendationService.getRecommendations(USERNAME, 0, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Illegal recommendation page or size.");

        assertThat(jdbcOperations.queryCount).isZero();
    }

    @Test
    void rebuildRecommendationsUsesListenerSignalsAndBlockedFilters () {
        final CapturingJdbcOperations jdbcOperations = new CapturingJdbcOperations();
        final RecommendationService recommendationService = new RecommendationService(listenerRepository(), jdbcOperations.proxy());

        recommendationService.rebuildRecommendations(USERNAME, 0, 5);

        assertThat(jdbcOperations.updateSqls).hasSize(2);
        assertThat(jdbcOperations.updateSqls.get(0)).contains("DELETE FROM listener_recommendation").contains("WHERE listener_id = ?");
        assertThat(jdbcOperations.updateSqls.get(1))
                .contains("listener_song_activity")
                .contains("listener_genre_priority")
                .contains("listener_preferred_genre")
                .contains("listener_following_performer")
                .contains("listener_performer_attitude")
                .contains("blocked_artist_penalty")
                .contains("* 20.0 AS penalty")
                .contains("blocked_song")
                .contains("blocked_performer")
                .contains("INSERT INTO listener_recommendation");
        assertThat(jdbcOperations.updateArguments.get(0)).containsExactly(LISTENER_ID);
        assertThat(jdbcOperations.updateArguments.get(1)).containsExactly(LISTENER_ID, 100);
        assertThat(jdbcOperations.arguments).containsExactly(LISTENER_ID, 5, 0);
    }

    private ListenerRepository listenerRepository () {
        final Listener listener = listenerWithId(LISTENER_ID);
        return (ListenerRepository) Proxy.newProxyInstance(
                ListenerRepository.class.getClassLoader(),
                new Class<?>[]{ListenerRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUsername")) {
                        return Optional.of(listener);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Listener listenerWithId (Long listenerId) {
        final Listener listener = Listener.register(
                USERNAME,
                "musiclover42@example.com",
                "password-hash",
                "Male",
                LocalDate.of(1995, 3, 15),
                "United States"
        );
        ReflectionTestUtils.setField(listener, "id", listenerId);
        return listener;
    }

    private static class CapturingJdbcOperations {
        private String sql;
        private Object[] arguments;
        private int queryCount;
        private final List<String> updateSqls = new ArrayList<>();
        private final List<Object[]> updateArguments = new ArrayList<>();

        public JdbcOperations proxy () {
            return (JdbcOperations) Proxy.newProxyInstance(
                    JdbcOperations.class.getClassLoader(),
                    new Class<?>[]{JdbcOperations.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("query")) {
                            queryCount++;
                            sql = (String) args[0];
                            arguments = (Object[]) args[2];
                            return List.of();
                        }
                        if (method.getName().equals("update")) {
                            updateSqls.add((String) args[0]);
                            updateArguments.add((Object[]) args[1]);
                            return 1;
                        }
                        if (method.getName().equals("queryForObject")) {
                            final String query = (String) args[0];
                            if (query.contains("song_stream")) {
                                return 0L;
                            }
                            if (query.contains("listener_recommendation")) {
                                return 1L;
                            }
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
