package com.musicapp.playlist;

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
}
