package com.musicapp.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    @EntityGraph(attributePaths = "performer")
    @Query("""
            SELECT a
            FROM Album a
            WHERE (:search = ''
                   OR LOWER(a.albumName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(a.performer.nickname) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:performerId IS NULL OR a.performer.id = :performerId)
            ORDER BY a.releaseDate DESC, a.albumName
            """)
    List<Album> findCatalog (@Param("search") String search, @Param("performerId") Long performerId);

    @EntityGraph(attributePaths = "performer")
    List<Album> findByPerformerIdOrderByReleaseDateDescAlbumName (Long performerId);
}
