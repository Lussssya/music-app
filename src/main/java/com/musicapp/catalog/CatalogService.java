package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.GenreResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final PerformerRepository performerRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final GenreRepository genreRepository;
    private final CatalogMapper catalogMapper;

    @Transactional(readOnly = true)
    public List<PerformerResponse> findPerformers (String search) {
        return performerRepository.findCatalog(normalizeTextFilter(search)).stream().map(catalogMapper::toPerformerResponse).toList();
    }

    @Transactional(readOnly = true)
    public PerformerResponse getPerformer (Long performerId) {
        return performerRepository.findById(performerId).map(catalogMapper::toPerformerResponse).orElseThrow(() -> new NotFoundException("Performer not found: " + performerId));
    }

    @Transactional(readOnly = true)
    public List<AlbumResponse> findAlbums (String search, Long performerId) {
        return albumRepository.findCatalog(normalizeTextFilter(search), performerId).stream().map(catalogMapper::toAlbumResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlbumResponse getAlbum (Long albumId) {
        return albumRepository.findById(albumId).map(catalogMapper::toAlbumResponse).orElseThrow(() -> new NotFoundException("Album not found: " + albumId));
    }

    @Transactional(readOnly = true)
    public List<AlbumResponse> findAlbumsByPerformer (Long performerId) {
        ensurePerformerExists(performerId);
        return albumRepository.findByPerformerIdOrderByReleaseDateDescAlbumName(performerId).stream().map(catalogMapper::toAlbumResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SongResponse> findSongs (String search, Long performerId, String genreName) {
        return songRepository.findCatalog(normalizeTextFilter(search), performerId, normalizeTextFilter(genreName)).stream().map(catalogMapper::toSongResponse).toList();
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
    public List<SongResponse> findSongsByPerformer (Long performerId) {
        ensurePerformerExists(performerId);
        return songRepository.findByMainPerformerIdOrderByReleaseDateDescTitle(performerId).stream().map(catalogMapper::toSongResponse).toList();
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
}
