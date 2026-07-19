package com.musicapp.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;

public interface PerformerRepository extends JpaRepository<Performer, Long> {
    @Query("""
            SELECT p
            FROM Performer p
            WHERE :search = ''
               OR LOWER(p.nickname) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY p.nickname
            """)
    Page<Performer> findCatalog (Pageable pageable, @Param("search") String search);
}
