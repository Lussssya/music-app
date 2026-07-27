package com.musicapp.catalog.dto;

public record SearchSuggestionResponse(
        String type,
        Long entityId,
        String title,
        String subtitle
) {
}
