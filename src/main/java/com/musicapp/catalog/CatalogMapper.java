package com.musicapp.catalog;

import com.musicapp.catalog.dto.AlbumResponse;
import com.musicapp.catalog.dto.AlbumSummary;
import com.musicapp.catalog.dto.GenreResponse;
import com.musicapp.catalog.dto.PerformerResponse;
import com.musicapp.catalog.dto.PerformerSummary;
import com.musicapp.catalog.dto.SongResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CatalogMapper {
    public PerformerResponse toPerformerResponse (Performer performer) {
        return new PerformerResponse(
                performer.getId(),
                performer.getNickname(),
                performer.getDescription(),
                performer.getPerformerType().name(),
                performer.isVerified(),
                performer.getPictureUrl()
        );
    }

    public PerformerSummary toPerformerSummary (Performer performer) {
        return new PerformerSummary(performer.getId(), performer.getNickname());
    }

    public AlbumResponse toAlbumResponse (Album album) {
        return new AlbumResponse(
                album.getId(),
                album.getAlbumName(),
                album.getAlbumUrl(),
                album.getReleaseDate(),
                toPerformerSummary(album.getPerformer())
        );
    }

    public AlbumSummary toAlbumSummary (Album album) {
        if (album == null) {
            return null;
        }
        return new AlbumSummary(album.getId(), album.getAlbumName(), album.getReleaseDate());
    }

    public GenreResponse toGenreResponse (Genre genre) {
        return new GenreResponse(genre.getName());
    }

    public SongResponse toSongResponse (Song song) {
        List<String> genres = song.getGenres()
                .stream()
                .map(Genre::getName)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new SongResponse(
                song.getId(),
                song.getTitle(),
                song.getSongUrl(),
                song.getReleaseDate(),
                song.getCredits(),
                song.getMoneyPerStream(),
                toPerformerSummary(song.getMainPerformer()),
                toAlbumSummary(song.getAlbum()),
                genres
        );
    }
}
