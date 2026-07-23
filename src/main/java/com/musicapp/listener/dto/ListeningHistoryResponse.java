package com.musicapp.listener.dto;

import com.musicapp.catalog.dto.SongResponse;

import java.time.Instant;

public record ListeningHistoryResponse(
        SongResponse song,
        Instant playedAt,
        boolean skipped
) {
}