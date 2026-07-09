package com.musicapp.playlist.dto;

import java.time.Instant;

public record PlaylistSongResponse(
        Long songId,
        String title,
        Long mainPerformerId,
        String mainPerformerName,
        Long addedByListenerId,
        Instant addedAt,
        int voteCount
) {
}
