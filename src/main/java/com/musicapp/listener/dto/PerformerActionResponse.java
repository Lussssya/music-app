package com.musicapp.listener.dto;

import com.musicapp.listener.ListenerAttitude;

import java.time.Instant;

public record PerformerActionResponse(
        Long listenerId,
        Long performerId,
        boolean following,
        ListenerAttitude attitude,
        boolean blocked,
        Instant followedAt,
        Instant blockedAt
) {
}
