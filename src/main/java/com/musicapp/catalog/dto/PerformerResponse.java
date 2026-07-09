package com.musicapp.catalog.dto;

public record PerformerResponse(
        Long performerId,
        String nickname,
        String description,
        String performerType,
        boolean verified,
        String pictureUrl
) {
}
