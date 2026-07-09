package com.musicapp.listener.dto;

import com.musicapp.listener.ListenerAttitude;

import java.time.Instant;

public record SongActionResponse(
        Long listenerId,
        Long songId,
        int streamCount,
        int skipCount,
        ListenerAttitude attitude,
        boolean blocked,
        Instant blockedAt
) {
}
