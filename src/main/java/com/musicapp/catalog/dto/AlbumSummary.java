package com.musicapp.catalog.dto;

import java.time.LocalDate;

public record AlbumSummary(
        Long albumId,
        String albumName,
        LocalDate releaseDate
) {
}
