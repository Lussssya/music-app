package com.musicapp;

import com.musicapp.catalog.*;
import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.GenreResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.GlobalExceptionHandler;
import com.musicapp.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

class CatalogControllerTest {
    private MockMvc mockMvc;
    private FakeCatalogService catalogService;

    @BeforeEach
    void setUp () {
        catalogService = new FakeCatalogService();
        mockMvc = MockMvcBuilders.standaloneSetup(new PerformerController(catalogService), new AlbumController(catalogService), new SongController(catalogService), new GenreController(catalogService)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void performersCanBeSearched () throws Exception {
        catalogService.performers = new PageImpl<>(List.of(performer()), PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/api/performers?search=aurora&page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].performerId").value(1)).andExpect(jsonPath("$.content[0].nickname").value("Aurora Sky")).andExpect(jsonPath("$.content[0].performerType").value("solo_artist")).andExpect(jsonPath("$.content[0].verified").value(true)).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));

        assertThat(catalogService.operation).isEqualTo("searchPerformers");
        assertThat(catalogService.search).isEqualTo("aurora");
        assertThat(catalogService.performersPage).isEqualTo(0);
        assertThat(catalogService.performersSize).isEqualTo(20);
    }

    @Test
    void performerNotFoundReturnsErrorResponseShape () throws Exception {
        catalogService.exception = new NotFoundException("Performer not found: 999");

        mockMvc.perform(get("/api/performers/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.error").value("Not Found")).andExpect(jsonPath("$.messages[0]").value("Performer not found: 999"));
    }

    @Test
    void albumsCanBeFilteredBySearchAndPerformer () throws Exception {
        catalogService.albums = new PageImpl<>(List.of(album()), PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/api/albums?search=electric&performerId=1&page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].albumId").value(2)).andExpect(jsonPath("$.content[0].albumName").value("Electric Dreams")).andExpect(jsonPath("$.content[0].performer.performerId").value(1)).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));

        assertThat(catalogService.operation).isEqualTo("searchAlbums");
        assertThat(catalogService.search).isEqualTo("electric");
        assertThat(catalogService.performerId).isEqualTo(1L);
        assertThat(catalogService.albumsPage).isEqualTo(0);
        assertThat(catalogService.albumsSize).isEqualTo(20);
    }

    @Test
    void albumNotFoundReturnsErrorResponseShape () throws Exception {
        catalogService.exception = new NotFoundException("Album not found: 999");

        mockMvc.perform(get("/api/albums/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.messages[0]").value("Album not found: 999"));
    }

    @Test
    void songsCanBeFilteredBySearchPerformerAndGenre () throws Exception {
        catalogService.songs = new PageImpl<>(List.of(song()), PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/api/songs?search=midnight&performerId=1&genreName=Pop&page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].songId").value(3)).andExpect(jsonPath("$.content[0].title").value("Midnight Sun")).andExpect(jsonPath("$.content[0].mainPerformer.nickname").value("Aurora Sky")).andExpect(jsonPath("$.content[0].genres[0]").value("Electronic")).andExpect(jsonPath("$.content[0].genres[1]").value("Pop")).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));

        assertThat(catalogService.operation).isEqualTo("searchSongs");
        assertThat(catalogService.search).isEqualTo("midnight");
        assertThat(catalogService.performerId).isEqualTo(1L);
        assertThat(catalogService.genreName).isEqualTo("Pop");
        assertThat(catalogService.songsPage).isEqualTo(0);
        assertThat(catalogService.songsSize).isEqualTo(20);
    }

    @Test
    void emptySongResultsReturnEmptyArray () throws Exception {
        catalogService.songs = Page.empty(PageRequest.of(0, 20));

        mockMvc.perform(get("/api/songs?search=does-not-exist&page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void songNotFoundReturnsErrorResponseShape () throws Exception {
        catalogService.exception = new NotFoundException("Song not found: 999");

        mockMvc.perform(get("/api/songs/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.messages[0]").value("Song not found: 999"));
    }

    @Test
    void nestedPerformerCatalogRoutesUsePerformerId () throws Exception {
        catalogService.albums = new PageImpl<>(List.of(album()), PageRequest.of(0, 20), 1);
        catalogService.songs = new PageImpl<>(List.of(song()), PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/api/performers/1/albums?page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].albumId").value(2)).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));
        assertThat(catalogService.operation).isEqualTo("searchAlbumsByPerformer");
        assertThat(catalogService.performerId).isEqualTo(1L);
        assertThat(catalogService.albumsPage).isEqualTo(0);
        assertThat(catalogService.albumsSize).isEqualTo(20);

        mockMvc.perform(get("/api/performers/1/songs?page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].songId").value(3)).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));
        assertThat(catalogService.operation).isEqualTo("searchSongsByPerformer");
        assertThat(catalogService.performerId).isEqualTo(1L);
        assertThat(catalogService.songsPage).isEqualTo(0);
        assertThat(catalogService.songsSize).isEqualTo(20);
    }

    @Test
    void genresReturnResponseShape () throws Exception {
        catalogService.genres = List.of(new GenreResponse("Electronic"), new GenreResponse("Pop"));

        mockMvc.perform(get("/api/genres")).andExpect(status().isOk()).andExpect(jsonPath("$[0].genreName").value("Electronic")).andExpect(jsonPath("$[1].genreName").value("Pop"));

        assertThat(catalogService.operation).isEqualTo("findGenres");
    }

    @Test
    void recentSongsSupportPagination () throws Exception {
        catalogService.recentSongs = new PageImpl<>(List.of(song()), PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/api/songs/recent?page=0&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].songId").value(3)).andExpect(jsonPath("$.content[0].title").value("Midnight Sun")).andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20));

        assertThat(catalogService.operation).isEqualTo("getAllRecentSongs");
        assertThat(catalogService.recentSongsPage).isEqualTo(0);
        assertThat(catalogService.recentSongsSize).isEqualTo(20);
    }

    @Test
    void recentSongsRejectIllegalPagination () throws Exception {
        catalogService.exception = new BadRequestException("Illegal page or size request. page: -1, size: 20");

        mockMvc.perform(get("/api/songs/recent?page=-1&size=20")).andExpect(status().isBadRequest());
    }

    private PerformerResponse performer () {
        return new PerformerResponse(1L, "Aurora Sky", "Rising pop sensation", "solo_artist", true, "https://images.com/aurora.jpg");
    }

    private AlbumResponse album () {
        return new AlbumResponse(2L, "Electric Dreams", "https://musicapp.com/album/electric-dreams", LocalDate.of(2024, 1, 20), new PerformerSummary(1L, "Aurora Sky"));
    }

    private SongResponse song () {
        return new SongResponse(3L, "Midnight Sun", "https://musicapp.com/songs/3", LocalDate.of(2024, 1, 20), "Written by Aurora Sky", new BigDecimal("0.0035"), new PerformerSummary(1L, "Aurora Sky"), new AlbumSummary(2L, "Electric Dreams", LocalDate.of(2024, 1, 20)), List.of("Electronic", "Pop"));
    }

    static class FakeCatalogService extends CatalogService {
        private Page<PerformerResponse> performers = Page.empty();
        private int performersPage;
        private int performersSize;
        private Page<AlbumResponse> albums = Page.empty();
        private int albumsPage;
        private int albumsSize;
        private Page<SongResponse> songs = Page.empty();
        private int songsPage;
        private int songsSize;
        private Page<SongResponse> recentSongs = Page.empty();
        private int recentSongsPage;
        private int recentSongsSize;
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
        public Page<PerformerResponse> searchPerformers (String search, int page, int size) {
            if (exception != null) {
                throw exception;
            }

            operation = "searchPerformers";
            this.search = search;
            performersPage = page;
            performersSize = size;
            return performers;
        }

        @Override
        public PerformerResponse getPerformer (Long performerId) {
            if (exception != null) {
                throw exception;
            }
            operation = "getPerformer";
            this.performerId = performerId;
            return performers.getContent().getFirst();
        }

        @Override
        public Page<AlbumResponse> searchAlbums (String search, Long performerId, int page, int size) {
            if (exception != null) {
                throw exception;
            }

            operation = "searchAlbums";
            this.search = search;
            this.performerId = performerId;
            albumsPage = page;
            albumsSize = size;
            return albums;
        }

        @Override
        public AlbumResponse getAlbum (Long albumId) {
            if (exception != null) {
                throw exception;
            }
            operation = "getAlbum";
            return albums.getContent().getFirst();
        }

        @Override
        public Page<AlbumResponse> searchAlbumsByPerformer (Long performerId, int page, int size) {
            if (exception != null) {
                throw exception;
            }

            operation = "searchAlbumsByPerformer";
            this.performerId = performerId;
            albumsPage = page;
            albumsSize = size;
            return albums;
        }

        @Override
        public Page<SongResponse> searchSongs (String search, Long performerId, String genreName, int page, int size) {
            if (exception != null) {
                throw exception;
            }

            operation = "searchSongs";
            this.search = search;
            this.performerId = performerId;
            this.genreName = genreName;
            songsPage = page;
            songsSize = size;
            return songs;
        }

        @Override
        public SongResponse getSong (Long songId) {
            if (exception != null) {
                throw exception;
            }
            operation = "getSong";
            return songs.getContent().getFirst();
        }

        @Override
        public Page<SongResponse> searchSongsByPerformer (Long performerId, int page, int size) {
            if (exception != null) {
                throw exception;
            }

            operation = "searchSongsByPerformer";
            this.performerId = performerId;
            songsPage = page;
            songsSize = size;
            return songs;
        }

        @Override
        public List<GenreResponse> findGenres () {
            operation = "findGenres";
            return genres;
        }

        @Override
        public Page<SongResponse> getAllRecentSongs (int page, int size) {
            if (exception != null) {
                throw exception;
            }

            operation = "getAllRecentSongs";
            recentSongsPage = page;
            recentSongsSize = size;
            return recentSongs;
        }
    }
}
