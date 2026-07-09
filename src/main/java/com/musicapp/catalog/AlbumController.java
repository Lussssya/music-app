package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {
    private final CatalogService catalogService;

    @GetMapping
    public List<AlbumResponse> findAlbums (
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long performerId
    ) {
        return catalogService.findAlbums(search, performerId);
    }

    @GetMapping("/{albumId}")
    public AlbumResponse getAlbum (@PathVariable Long albumId) {
        return catalogService.getAlbum(albumId);
    }
}
