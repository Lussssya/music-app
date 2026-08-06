package com.musicapp.playlist.dto;

import com.musicapp.playlist.PlaylistType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlaylistRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 128, message = "must not exceed 128 characters")
        String name,
        @NotNull PlaylistType type,
        @Size(max = 2048, message = "must not exceed 2048 characters") String playlistUrl,
        @Size(max = 2048, message = "must not exceed 2048 characters") String pictureUrl
) {
}
