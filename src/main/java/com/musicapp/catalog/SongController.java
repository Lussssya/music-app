package com.musicapp.catalog;

import com.musicapp.catalog.dto.SongResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
public class SongController {
    private final CatalogService catalogService;

    @GetMapping
    public Page<SongResponse> findSongs (@RequestParam(required = false) String search, @RequestParam(required = false) Long performerId, @RequestParam(required = false) String genreName, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return catalogService.searchSongs(search, performerId, genreName, page, size);
    }

    @GetMapping("/{songId}")
    public SongResponse getSong (@PathVariable Long songId) {
        return catalogService.getSong(songId);
    }

    @GetMapping("/recent")
    public Page<SongResponse> getLatestReleases(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return catalogService.getAllRecentSongs(page, size);
    }
}
