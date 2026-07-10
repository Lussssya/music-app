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

        recommendationService.getRecommendations(USERNAME, 10);

        assertThat(jdbcOperations.sql)
                .contains("ORDER BY lr.recommendation_score DESC")
                .contains("lr.generated_at DESC")
                .contains("LIMIT ?");
        assertThat(jdbcOperations.arguments).containsExactly(LISTENER_ID, 10);
    }

    @Test
    void recommendationLimitIsCapped () {
        CapturingJdbcOperations jdbcOperations = new CapturingJdbcOperations();
        RecommendationService recommendationService = new RecommendationService(
                listenerRepository(),
                jdbcOperations.proxy()
        );

        recommendationService.getRecommendations(USERNAME, 150);

        assertThat(jdbcOperations.arguments).containsExactly(LISTENER_ID, 100);
    }

    @Test
    void recommendationLimitMustBePositive () {
        final CapturingJdbcOperations jdbcOperations = new CapturingJdbcOperations();
        final RecommendationService recommendationService = new RecommendationService(listenerRepository(), jdbcOperations.proxy());

        assertThatThrownBy(() -> recommendationService.getRecommendations(USERNAME, 0)).isInstanceOf(BadRequestException.class).hasMessage("Recommendation limit must be at least 1.");

        assertThat(jdbcOperations.queryCount).isZero();
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
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
