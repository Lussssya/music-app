package com.musicapp.playlist.dto;

import java.time.Instant;

public record PlaylistMemberResponse(
        Long listenerId,
        String username,
        Instant joinedAt
) {
}
