package com.musicapp.recommendation;

import com.musicapp.recommendation.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendationService;

    @GetMapping
    public List<RecommendationResponse> getRecommendations (Authentication authentication, @RequestParam(defaultValue = "20") int limit) {
        return recommendationService.getRecommendations(authentication.getName(), limit);
    }

    @PostMapping("/rebuild")
    public List<RecommendationResponse> rebuildRecommendations (Authentication authentication, @RequestParam(defaultValue = "20") int limit) {
        return recommendationService.rebuildRecommendations(authentication.getName(), limit);
    }
}
