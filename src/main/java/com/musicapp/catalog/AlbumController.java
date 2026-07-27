package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.SongResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {
    private final CatalogService catalogService;

    @GetMapping
    public Page<AlbumResponse> findAlbums (@RequestParam(required = false) String search, @RequestParam(required = false) Long performerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return catalogService.searchAlbums(search, performerId, page, size);
    }

    @GetMapping("/{albumId}")
    public AlbumResponse getAlbum (@PathVariable Long albumId) {
        return catalogService.getAlbum(albumId);
    }

    @GetMapping("/{albumId}/songs")
    public Page<SongResponse> findSongsByAlbum (@PathVariable Long albumId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return catalogService.searchSongsByAlbum(albumId, page, size);
    }
}
