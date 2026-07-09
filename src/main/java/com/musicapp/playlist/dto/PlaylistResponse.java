package com.musicapp.playlist.dto;

import java.time.Instant;
import java.util.List;

public record PlaylistResponse(
        Long playlistId,
        String name,
        String type,
        String playlistUrl,
        String pictureUrl,
        Long creatorId,
        String creatorUsername,
        Instant createdAt,
        int memberCount,
        int songCount,
        List<PlaylistMemberResponse> members,
        List<PlaylistSongResponse> songs
) {
}
