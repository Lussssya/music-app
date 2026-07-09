package com.musicapp.catalog.dto;

import java.time.LocalDate;

public record AlbumResponse(
        Long albumId,
        String albumName,
        String albumUrl,
        LocalDate releaseDate,
        PerformerSummary performer
) {
}
