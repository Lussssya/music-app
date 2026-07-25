package com.musicapp.playlist;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface GeneratedPlaylistRepository extends JpaRepository<GeneratedPlaylist, Long> {

    Optional<GeneratedPlaylist> findByListenerIdAndPlaylistType (Long listenerId, GeneratedPlaylistType playlistType);

    List<GeneratedPlaylist> findAllByListenerId (Long listenerId);

    @Modifying
    @Query("""
                delete from GeneratedPlaylist
                where expiresAt < CURRENT_TIMESTAMP
            """)
    void deleteExpiredPlaylists ();
}
