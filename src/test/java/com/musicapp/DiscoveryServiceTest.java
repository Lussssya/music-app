package com.musicapp;

import com.musicapp.catalog.CatalogService;
import com.musicapp.catalog.DiscoveryService;
import com.musicapp.common.BadRequestException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveryServiceTest {

    @Test
    void trendingUsesRecentMeaningfulStreamsAndStableTieBreakers () {
        final CapturingJdbcOperations jdbc = new CapturingJdbcOperations();
        final DiscoveryService service = service(jdbc, listenerRepository());

        service.getTrendingSongs(0, 20, 30);

        assertThat(jdbc.querySql)
                .contains("skipped = FALSE")
                .contains("CURRENT_TIMESTAMP - (? * INTERVAL '1 day')")
                .contains("COUNT(DISTINCT listener_id)")
                .contains("ORDER BY stream_count DESC, listener_count DESC, latest_stream DESC, song_id");
        assertThat(jdbc.queryArguments).containsExactly(30, 20, 0L);
        assertThat(jdbc.queryForObjectSql).contains("GROUP BY song_id");
    }

    @Test
    void followedFeedUsesFollowStateAndRemovesBlockedContent () {
        final CapturingJdbcOperations jdbc = new CapturingJdbcOperations();
        final DiscoveryService service = service(jdbc, listenerRepository());

        service.getFollowedPerformerReleases("musiclover42", 0, 20);

        assertThat(jdbc.queryForListSql)
                .contains("listener_following_performer")
                .contains("blocked_song")
                .contains("blocked_performer")
                .contains("ORDER BY s.release_date DESC");
        assertThat(jdbc.queryForListArguments).containsExactly(1L, 20, 0L);
    }

    @Test
    void discoveryRequestLimitsAreValidated () {
        final DiscoveryService service = service(new CapturingJdbcOperations(), listenerRepository());

        assertThatThrownBy(() -> service.getTrendingSongs(0, 51, 30))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Illegal discovery page or size.");
        assertThatThrownBy(() -> service.getTrendingSongs(0, 20, 91))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Trending days must be between 1 and 90.");
        assertThatThrownBy(() -> service.getSearchSuggestions("music", 11))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Suggestion limit must be between 1 and 10.");
        assertThat(service.getSearchSuggestions("a", 8)).isEmpty();
    }

    @Test
    void suggestionsUseOneBoundedQueryWithRelevanceOrdering () {
        final CapturingJdbcOperations jdbc = new CapturingJdbcOperations();
        final DiscoveryService service = service(jdbc, listenerRepository());

        service.getSearchSuggestions(" aur ", 8);

        assertThat(jdbc.querySql)
                .contains("UNION ALL")
                .contains("WHEN LOWER(title) = search_input.search_term THEN 0")
                .contains("LIMIT ?");
        assertThat(jdbc.queryArguments).containsExactly("aur", 8);
    }

    private DiscoveryService service (CapturingJdbcOperations jdbc, ListenerRepository listenerRepository) {
        return new DiscoveryService(
                listenerRepository,
                new EmptyCatalogService(),
                jdbc.proxy()
        );
    }

    private ListenerRepository listenerRepository () {
        final Listener listener = Listener.register(
                "musiclover42",
                "musiclover42@example.com",
                "password-hash",
                "Male",
                LocalDate.of(1995, 3, 15),
                "United States"
        );
        ReflectionTestUtils.setField(listener, "id", 1L);

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

    private static class EmptyCatalogService extends CatalogService {
        EmptyCatalogService () {
            super(null, null, null, null, null);
        }

        @Override
        public Map<Long, com.musicapp.catalog.dto.SongResponse> getSongsByIds (List<Long> songIds) {
            return Map.of();
        }
    }

    private static class CapturingJdbcOperations {
        private String querySql;
        private Object[] queryArguments;
        private String queryForListSql;
        private Object[] queryForListArguments;
        private String queryForObjectSql;

        JdbcOperations proxy () {
            return (JdbcOperations) Proxy.newProxyInstance(
                    JdbcOperations.class.getClassLoader(),
                    new Class<?>[]{JdbcOperations.class},
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "query" -> {
                                querySql = (String) args[0];
                                queryArguments = (Object[]) args[2];
                                yield List.of();
                            }
                            case "queryForList" -> {
                                queryForListSql = (String) args[0];
                                queryForListArguments = (Object[]) args[2];
                                yield List.of();
                            }
                            case "queryForObject" -> {
                                queryForObjectSql = (String) args[0];
                                yield 0L;
                            }
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
            );
        }
    }
}
