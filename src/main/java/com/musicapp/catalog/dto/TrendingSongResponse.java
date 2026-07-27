package com.musicapp.catalog.dto;

public record TrendingSongResponse(
        SongResponse song,
        long streamCount,
        long listenerCount
) {
}
