package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.SongResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/performers")
@RequiredArgsConstructor
public class PerformerController {
    private final CatalogService catalogService;

    @GetMapping
    public Page<PerformerResponse> findPerformers (@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return catalogService.searchPerformers(search, page, size);
    }

    @GetMapping("/{performerId}")
    public PerformerResponse getPerformer (@PathVariable Long performerId) {
        return catalogService.getPerformer(performerId);
    }

    @GetMapping("/{performerId}/albums")
    public Page<AlbumResponse> findAlbumsByPerformer (@PathVariable Long performerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return catalogService.searchAlbumsByPerformer(performerId, page, size);
    }

    @GetMapping("/{performerId}/songs")
    public Page<SongResponse> findSongsByPerformer (@PathVariable Long performerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return catalogService.searchSongsByPerformer(performerId, page, size);
    }
}
