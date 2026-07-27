package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.GenreResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PerformerRepository performerRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final GenreRepository genreRepository;
    private final CatalogMapper catalogMapper;

    @Transactional(readOnly = true)
    public Page<PerformerResponse> searchPerformers (String search, int page, int size) {
        final Pageable pageable = createPageable(page, size);
        return performerRepository.findCatalog(pageable, normalizeTextFilter(search)).map(catalogMapper::toPerformerResponse);
    }

    @Transactional(readOnly = true)
    public PerformerResponse getPerformer (Long performerId) {
        return performerRepository.findById(performerId).map(catalogMapper::toPerformerResponse).orElseThrow(() -> new NotFoundException("Performer not found: " + performerId));
    }

    @Transactional(readOnly = true)
    public Page<AlbumResponse> searchAlbums (String search, Long performerId, int page, int size) {
        final Pageable pageable = createPageable(page, size);
        return albumRepository.findCatalog(pageable, normalizeTextFilter(search), performerId).map(catalogMapper::toAlbumResponse);
    }

    @Transactional(readOnly = true)
    public AlbumResponse getAlbum (Long albumId) {
        return albumRepository.findById(albumId).map(catalogMapper::toAlbumResponse).orElseThrow(() -> new NotFoundException("Album not found: " + albumId));
    }

    @Transactional(readOnly = true)
    public Page<AlbumResponse> searchAlbumsByPerformer (Long performerId, int page, int size) {
        final Pageable pageable = createPageable(page, size);
        ensurePerformerExists(performerId);
        return albumRepository.findByPerformerIdOrderByReleaseDateDescAlbumName(pageable, performerId).map(catalogMapper::toAlbumResponse);
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> searchSongs (String search, Long performerId, String genreName, int page, int size) {
        final Pageable pageable = createPageable(page, size);
        return songRepository.findCatalog(pageable, normalizeTextFilter(search), performerId, normalizeTextFilter(genreName)).map(catalogMapper::toSongResponse);
    }

    private String normalizeTextFilter (String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public SongResponse getSong (Long songId) {
        return songRepository.findById(songId).map(catalogMapper::toSongResponse).orElseThrow(() -> new NotFoundException("Song not found: " + songId));
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> searchSongsByPerformer (Long performerId, int page, int size) {
        final Pageable pageable = createPageable(page, size);
        ensurePerformerExists(performerId);
        return songRepository.findByMainPerformerIdOrderByReleaseDateDescTitle(pageable, performerId).map(catalogMapper::toSongResponse);
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> searchSongsByAlbum (Long albumId, int page, int size) {
        final Pageable pageable = createPageable(page, size);

        if (!albumRepository.existsById(albumId)) {
            throw new NotFoundException("Album not found: " + albumId);
        }
        return songRepository.findByAlbumIdOrderByReleaseDateDescTitle(pageable, albumId).map(catalogMapper::toSongResponse);
    }

    private void ensurePerformerExists (Long performerId) {
        if (!performerRepository.existsById(performerId)) {
            throw new NotFoundException("Performer not found: " + performerId);
        }
    }

    @Transactional(readOnly = true)
    public List<GenreResponse> findGenres () {
        return genreRepository.findAllByOrderByNameAsc().stream().map(catalogMapper::toGenreResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> getAllRecentSongs (int page, int size) {
        final Pageable pageable = createPageable(page, size);
        return songRepository.findAllByOrderByReleaseDateDescTitle(pageable).map(catalogMapper::toSongResponse);
    }

    private Pageable createPageable (int page, int size) {
        ensurePaginationParameters(page, size);
        return PageRequest.of(page, size);
    }

    private void ensurePaginationParameters (int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Illegal page or size request. page: " + page + ", size: " + size);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, SongResponse> getSongsByIds (List<Long> songIds) {
        if (songIds.isEmpty()) {
            return Map.of();
        }

        return songRepository.findByIdInOrderByTitleAsc(songIds).stream().collect(Collectors.toMap(Song::getId, catalogMapper::toSongResponse));
    }
}
