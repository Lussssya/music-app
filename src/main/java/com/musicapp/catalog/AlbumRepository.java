package com.musicapp.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import org.springframework.data.domain.Page;

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
    Page<Album> findCatalog (Pageable pageable, @Param("search") String search, @Param("performerId") Long performerId);

    @EntityGraph(attributePaths = "performer")
    Page<Album> findByPerformerIdOrderByReleaseDateDescAlbumName (Pageable pageable, Long performerId);
}
