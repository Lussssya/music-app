package com.musicapp.recommendation;

import com.musicapp.recommendation.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendationService;

    @GetMapping
    public Page<RecommendationResponse> getRecommendations (Authentication authentication, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return recommendationService.getRecommendations(authentication.getName(), page, size);
    }

    @PostMapping("/rebuild")
    public Page<RecommendationResponse> rebuildRecommendations (Authentication authentication, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return recommendationService.rebuildRecommendations(authentication.getName(), page, size);
    }
}
