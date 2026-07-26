CREATE INDEX idx_song_stream_listener_streamed_at
    ON song_stream (listener_id, streamed_at DESC);

CREATE INDEX idx_listener_song_activity_listener_updated_at
    ON listener_song_activity (listener_id, updated_at DESC);

CREATE INDEX idx_listener_song_activity_listener_streams
    ON listener_song_activity (listener_id, stream_count DESC);
