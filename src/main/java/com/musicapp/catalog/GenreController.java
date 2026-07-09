package com.musicapp.catalog;

import com.musicapp.catalog.dto.GenreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {
    private final CatalogService catalogService;

    @GetMapping
    public List<GenreResponse> findGenres () {
        return catalogService.findGenres();
    }
}
