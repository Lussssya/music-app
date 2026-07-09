package com.musicapp.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformerRepository extends JpaRepository<Performer, Long> {
    @Query("""
            SELECT p
            FROM Performer p
            WHERE :search = ''
               OR LOWER(p.nickname) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY p.nickname
            """)
    List<Performer> findCatalog (@Param("search") String search);
}
