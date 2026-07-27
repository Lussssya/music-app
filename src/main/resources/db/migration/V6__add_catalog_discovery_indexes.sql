CREATE INDEX idx_song_main_performer_release_date
    ON song (main_performer_id, release_date DESC);

CREATE INDEX idx_song_stream_trending_window
    ON song_stream (streamed_at DESC, song_id)
    INCLUDE (listener_id)
    WHERE skipped = FALSE;
