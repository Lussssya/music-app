package com.musicapp.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {
    @EntityGraph(attributePaths = {"mainPerformer", "album", "genres"})
    @Query("""
            SELECT DISTINCT s
            FROM Song s
            LEFT JOIN s.genres g
            LEFT JOIN s.album a
            WHERE (:search = ''
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(s.mainPerformer.nickname) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(a.albumName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:performerId IS NULL OR s.mainPerformer.id = :performerId)
              AND (:genreName = '' OR LOWER(g.name) = LOWER(:genreName))
            ORDER BY s.releaseDate DESC, s.title
            """)
    Page<Song> findCatalog (
            Pageable pageable,
            @Param("search") String search,
            @Param("performerId") Long performerId,
            @Param("genreName") String genreName
    );

    @EntityGraph(attributePaths = {"mainPerformer", "album", "genres"})
    Page<Song> findByMainPerformerIdOrderByReleaseDateDescTitle (Pageable pageable, Long performerId);

    @EntityGraph(attributePaths = {"mainPerformer", "album", "genres"})
    Page<Song> findByAlbumIdOrderByReleaseDateDescTitle (Pageable pageable, Long albumId);

    @EntityGraph(attributePaths = {"mainPerformer", "album", "genres"})
    List<Song> findByIdInOrderByTitleAsc (List<Long> songIds);

    @EntityGraph(attributePaths = {"mainPerformer", "album", "genres"})
    Page<Song> findAllByOrderByReleaseDateDescTitle(Pageable pageable);
}
