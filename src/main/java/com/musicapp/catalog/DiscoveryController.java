package com.musicapp.catalog;

import com.musicapp.catalog.dto.SearchSuggestionResponse;
import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.catalog.dto.TrendingSongResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {
    private final DiscoveryService discoveryService;

    @GetMapping("/trending")
    public Page<TrendingSongResponse> getTrendingSongs (@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "30") int days) {
        return discoveryService.getTrendingSongs(page, size, days);
    }

    @GetMapping("/suggestions")
    public List<SearchSuggestionResponse> getSearchSuggestions (@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "8") int limit) {
        return discoveryService.getSearchSuggestions(query, limit);
    }

    @GetMapping("/following/releases")
    public Page<SongResponse> getFollowedPerformerReleases (Authentication authentication, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return discoveryService.getFollowedPerformerReleases(authentication.getName(), page, size);
    }
}
