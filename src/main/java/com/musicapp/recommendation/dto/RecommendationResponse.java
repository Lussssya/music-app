package com.musicapp.recommendation.dto;

import com.musicapp.catalog.dto.SongResponse;

import java.math.BigDecimal;
import java.time.Instant;

public record RecommendationResponse(
        BigDecimal score,
        Instant generatedAt,
        SongResponse song
) {
}
