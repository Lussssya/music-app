package com.musicapp.playlist.dto;

import com.musicapp.catalog.dto.SongResponse;
import com.musicapp.playlist.GeneratedPlaylistType;

import java.util.List;

public record GeneratedPlaylistResponse(
        GeneratedPlaylistType type,
        String name,
        String description,
        List<SongResponse> songs
) {}
