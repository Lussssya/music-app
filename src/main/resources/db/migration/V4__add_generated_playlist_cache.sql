CREATE TABLE generated_playlist
(
    generated_playlist_id BIGSERIAL PRIMARY KEY,
    listener_id           BIGINT      NOT NULL REFERENCES listener(listener_id) ON DELETE CASCADE,
    playlist_type         VARCHAR(64) NOT NULL,
    generated_at          TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    UNIQUE (listener_id, playlist_type)
);

CREATE TABLE generated_playlist_song
(
    generated_playlist_song_id BIGSERIAL PRIMARY KEY,
    generated_playlist_id      BIGINT  NOT NULL REFERENCES generated_playlist(generated_playlist_id) ON DELETE CASCADE,
    song_id                    BIGINT  NOT NULL REFERENCES song(song_id),
    position                   INTEGER NOT NULL,
    UNIQUE (generated_playlist_id, position)
);

CREATE INDEX idx_generated_playlist_expiry ON generated_playlist (expires_at);
