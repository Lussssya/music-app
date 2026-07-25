package com.musicapp.playlist.dto;

import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.playlist.GeneratedPlaylistType;

import java.util.List;
import java.time.Instant;

public record GeneratedPlaylistResponse(
        GeneratedPlaylistType type,
        String name,
        String description,
        Instant generatedAt,
        Instant expiresAt,
        List<SongResponse> songs
) {}
