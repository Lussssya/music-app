ALTER TABLE listener_song_activity
    ADD COLUMN attitude_updated_at TIMESTAMPTZ;

UPDATE listener_song_activity
SET attitude_updated_at = updated_at
WHERE attitude IS NOT NULL;

CREATE INDEX idx_listener_song_activity_listener_liked_at
    ON listener_song_activity (listener_id, attitude_updated_at DESC, song_id DESC)
    WHERE attitude = 'like'::attitude;
