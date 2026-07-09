package com.musicapp.playlist.dto;

import java.time.Instant;

public record PlaylistSummaryResponse(
        Long playlistId,
        String name,
        String type,
        String playlistUrl,
        String pictureUrl,
        Long creatorId,
        String creatorUsername,
        Instant createdAt,
        int memberCount,
        int songCount
) {
}
