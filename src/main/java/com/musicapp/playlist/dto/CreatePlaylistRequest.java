package com.musicapp.playlist.dto;

import com.musicapp.playlist.PlaylistType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlaylistRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull PlaylistType type,
        String playlistUrl,
        String pictureUrl
) {
}
