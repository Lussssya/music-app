package com.musicapp.catalog;

import com.musicapp.catalog.dto.SongResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
public class SongController {
    private final CatalogService catalogService;

    @GetMapping
    public List<SongResponse> findSongs (@RequestParam(required = false) String search, @RequestParam(required = false) Long performerId, @RequestParam(required = false) String genreName) {
        return catalogService.findSongs(search, performerId, genreName);
    }

    @GetMapping("/{songId}")
    public SongResponse getSong (@PathVariable Long songId) {
        return catalogService.getSong(songId);
    }
}
