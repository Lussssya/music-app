package com.musicapp.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SongResponse(
        Long songId,
        String title,
        String songUrl,
        LocalDate releaseDate,
        String credits,
        BigDecimal moneyPerStream,
        PerformerSummary mainPerformer,
        AlbumSummary album,
        List<String> genres
) {
}
