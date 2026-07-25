package com.musicapp.playlist.dto;

import com.musicapp.playlist.GeneratedPlaylistType;

public record GeneratedPlaylistSummaryResponse(
        GeneratedPlaylistType type,
        String name,
        String description
) {
}