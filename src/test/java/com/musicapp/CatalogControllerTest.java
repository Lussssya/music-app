package com.musicapp;

import com.musicapp.catalog.*;
import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.GenreResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.GlobalExceptionHandler;
import com.musicapp.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogControllerTest {
    private MockMvc mockMvc;
    private FakeCatalogService catalogService;

    @BeforeEach
    void setUp () {
        catalogService = new FakeCatalogService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PerformerController(catalogService),
                        new AlbumController(catalogService),
                        new SongController(catalogService),
                        new GenreController(catalogService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void performersCanBeSearched () throws Exception {
        catalogService.performers = List.of(performer());

        mockMvc.perform(get("/api/performers?search=aurora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performerId").value(1))
                .andExpect(jsonPath("$[0].nickname").value("Aurora Sky"))
                .andExpect(jsonPath("$[0].performerType").value("solo_artist"))
                .andExpect(jsonPath("$[0].verified").value(true));

        assertThat(catalogService.operation).isEqualTo("findPerformers");
        assertThat(catalogService.search).isEqualTo("aurora");
    }

    @Test
    void performerNotFoundReturnsErrorResponseShape () throws Exception {
        catalogService.exception = new NotFoundException("Performer not found: 999");

        mockMvc.perform(get("/api/performers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.messages[0]").value("Performer not found: 999"));
    }

    @Test
    void albumsCanBeFilteredBySearchAndPerformer () throws Exception {
        catalogService.albums = List.of(album());

        mockMvc.perform(get("/api/albums?search=electric&performerId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].albumId").value(2))
                .andExpect(jsonPath("$[0].albumName").value("Electric Dreams"))
                .andExpect(jsonPath("$[0].performer.performerId").value(1));

        assertThat(catalogService.operation).isEqualTo("findAlbums");
        assertThat(catalogService.search).isEqualTo("electric");
        assertThat(catalogService.performerId).isEqualTo(1L);
    }

    @Test
    void albumNotFoundReturnsErrorResponseShape () throws Exception {
        catalogService.exception = new NotFoundException("Album not found: 999");

        mockMvc.perform(get("/api/albums/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.messages[0]").value("Album not found: 999"));
    }

    @Test
    void songsCanBeFilteredBySearchPerformerAndGenre () throws Exception {
        catalogService.songs = List.of(song());

        mockMvc.perform(get("/api/songs?search=midnight&performerId=1&genreName=Pop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].songId").value(3))
                .andExpect(jsonPath("$[0].title").value("Midnight Sun"))
                .andExpect(jsonPath("$[0].mainPerformer.nickname").value("Aurora Sky"))
                .andExpect(jsonPath("$[0].genres[0]").value("Electronic"))
                .andExpect(jsonPath("$[0].genres[1]").value("Pop"));

        assertThat(catalogService.operation).isEqualTo("findSongs");
        assertThat(catalogService.search).isEqualTo("midnight");
        assertThat(catalogService.performerId).isEqualTo(1L);
        assertThat(catalogService.genreName).isEqualTo("Pop");
    }

    @Test
    void emptySongResultsReturnEmptyArray () throws Exception {
        catalogService.songs = List.of();

        mockMvc.perform(get("/api/songs?search=does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void songNotFoundReturnsErrorResponseShape () throws Exception {
        catalogService.exception = new NotFoundException("Song not found: 999");

        mockMvc.perform(get("/api/songs/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.messages[0]").value("Song not found: 999"));
    }

    @Test
    void nestedPerformerCatalogRoutesUsePerformerId () throws Exception {
        catalogService.albums = List.of(album());
        catalogService.songs = List.of(song());

        mockMvc.perform(get("/api/performers/1/albums")).andExpect(status().isOk()).andExpect(jsonPath("$[0].albumId").value(2));
        assertThat(catalogService.operation).isEqualTo("findAlbumsByPerformer");
        assertThat(catalogService.performerId).isEqualTo(1L);

        mockMvc.perform(get("/api/performers/1/songs")).andExpect(status().isOk()).andExpect(jsonPath("$[0].songId").value(3));
        assertThat(catalogService.operation).isEqualTo("findSongsByPerformer");
        assertThat(catalogService.performerId).isEqualTo(1L);
    }

    @Test
    void genresReturnResponseShape () throws Exception {
        catalogService.genres = List.of(new GenreResponse("Electronic"), new GenreResponse("Pop"));

        mockMvc.perform(get("/api/genres")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].genreName").value("Electronic"))
                .andExpect(jsonPath("$[1].genreName").value("Pop"));

        assertThat(catalogService.operation).isEqualTo("findGenres");
    }

    private PerformerResponse performer () {
        return new PerformerResponse(
                1L,
                "Aurora Sky",
                "Rising pop sensation",
                "solo_artist",
                true,
                "https://images.com/aurora.jpg"
        );
    }

    private AlbumResponse album () {
        return new AlbumResponse(
                2L,
                "Electric Dreams",
                "https://musicapp.com/album/electric-dreams",
                LocalDate.of(2024, 1, 20),
                new PerformerSummary(1L, "Aurora Sky")
        );
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

    static class FakeCatalogService extends CatalogService {
        private List<PerformerResponse> performers = List.of();
        private List<AlbumResponse> albums = List.of();
        private List<SongResponse> songs = List.of();
        private List<GenreResponse> genres = List.of();
        private RuntimeException exception;
        private String operation;
        private String search;
        private Long performerId;
        private String genreName;

        FakeCatalogService () {
            super(null, null, null, null, null);
        }

        @Override
        public List<PerformerResponse> findPerformers (String search) {
            operation = "findPerformers";
            this.search = search;
            return performers;
        }

        @Override
        public PerformerResponse getPerformer (Long performerId) {
            if (exception != null) {
                throw exception;
            }
            operation = "getPerformer";
            this.performerId = performerId;
            return performers.getFirst();
        }

        @Override
        public List<AlbumResponse> findAlbums (String search, Long performerId) {
            operation = "findAlbums";
            this.search = search;
            this.performerId = performerId;
            return albums;
        }

        @Override
        public AlbumResponse getAlbum (Long albumId) {
            if (exception != null) {
                throw exception;
            }
            operation = "getAlbum";
            return albums.getFirst();
        }

        @Override
        public List<AlbumResponse> findAlbumsByPerformer (Long performerId) {
            operation = "findAlbumsByPerformer";
            this.performerId = performerId;
            return albums;
        }

        @Override
        public List<SongResponse> findSongs (String search, Long performerId, String genreName) {
            operation = "findSongs";
            this.search = search;
            this.performerId = performerId;
            this.genreName = genreName;
            return songs;
        }

        @Override
        public SongResponse getSong (Long songId) {
            if (exception != null) {
                throw exception;
            }
            operation = "getSong";
            return songs.getFirst();
        }

        @Override
        public List<SongResponse> findSongsByPerformer (Long performerId) {
            operation = "findSongsByPerformer";
            this.performerId = performerId;
            return songs;
        }

        @Override
        public List<GenreResponse> findGenres () {
            operation = "findGenres";
            return genres;
        }
    }
}
