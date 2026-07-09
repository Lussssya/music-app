package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.SongResponse;
import lombok.RequiredArgsConstructor;
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
    public List<PerformerResponse> findPerformers (@RequestParam(required = false) String search) {
        return catalogService.findPerformers(search);
    }

    @GetMapping("/{performerId}")
    public PerformerResponse getPerformer (@PathVariable Long performerId) {
        return catalogService.getPerformer(performerId);
    }

    @GetMapping("/{performerId}/albums")
    public List<AlbumResponse> findAlbumsByPerformer (@PathVariable Long performerId) {
        return catalogService.findAlbumsByPerformer(performerId);
    }

    @GetMapping("/{performerId}/songs")
    public List<SongResponse> findSongsByPerformer (@PathVariable Long performerId) {
        return catalogService.findSongsByPerformer(performerId);
    }
}
