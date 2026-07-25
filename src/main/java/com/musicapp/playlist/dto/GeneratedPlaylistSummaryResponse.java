package com.musicapp.playlist.dto;

import com.musicapp.playlist.GeneratedPlaylistType;
import java.time.Instant;

public record GeneratedPlaylistSummaryResponse(
        GeneratedPlaylistType type,
        String name,
        String description,
        boolean available,
        Instant generatedAt,
        Instant expiresAt
) {
}
