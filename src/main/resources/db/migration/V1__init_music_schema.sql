CREATE TYPE performer_type AS ENUM (
    'solo_artist',
    'duo',
    'band',
    'group',
    'collective',
    'orchestra',
    'soundtrack',
    'other'
);

CREATE TYPE performer_role AS ENUM (
    'main',
    'featuring'
);

CREATE TYPE attitude AS ENUM (
    'like',
    'dislike',
    'not_interested'
);

CREATE TYPE playlist_type AS ENUM (
    'private',
    'public',
    'shared'
);

CREATE TABLE subscription_plan
(
    plan_name            VARCHAR(64) PRIMARY KEY,
    price                NUMERIC(8, 2) NOT NULL CHECK (price >= 0),
    plan_duration_months INTEGER       NOT NULL CHECK (plan_duration_months >= 0)
);

CREATE TABLE country
(
    country_name VARCHAR(64) PRIMARY KEY
);

CREATE TABLE genre
(
    genre_name VARCHAR(64) PRIMARY KEY
);

CREATE TABLE listener
(
    listener_id   BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email_address VARCHAR(128) NOT NULL UNIQUE
        CHECK (email_address ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'
) ,
    password_hash VARCHAR(255) NOT NULL,
    gender VARCHAR(32) NOT NULL,
    date_of_birth DATE NOT NULL,
    picture_url TEXT CHECK (picture_url IS NULL OR picture_url ~* '^https?://'),
    country_name VARCHAR(64) NOT NULL REFERENCES country(country_name),
    balance NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    plan_name VARCHAR(64) NOT NULL DEFAULT 'free' REFERENCES subscription_plan(plan_name),
    plan_start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE performer
(
    performer_id   BIGSERIAL PRIMARY KEY,
    nickname       VARCHAR(128)   NOT NULL UNIQUE,
    description    TEXT,
    performer_type performer_type NOT NULL,
    verified       BOOLEAN        NOT NULL DEFAULT FALSE,
    picture_url    TEXT CHECK (picture_url IS NULL OR picture_url ~* '^https?://'
) ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE album
(
    album_id     BIGSERIAL PRIMARY KEY,
    performer_id BIGINT       NOT NULL REFERENCES performer (performer_id),
    album_name   VARCHAR(128) NOT NULL,
    album_url    TEXT CHECK (album_url IS NULL OR album_url ~* '^https?://'
) ,
    release_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (performer_id, album_name)
);

CREATE TABLE song
(
    song_id  BIGSERIAL PRIMARY KEY,
    song_url TEXT CHECK (song_url IS NULL OR song_url ~* '^https?://'
) ,
    title VARCHAR(128) NOT NULL,
    release_date DATE NOT NULL,
    lyrics TEXT,
    credits TEXT,
    money_per_stream NUMERIC(12, 6) NOT NULL DEFAULT 0 CHECK (money_per_stream >= 0),
    main_performer_id BIGINT NOT NULL REFERENCES performer(performer_id),
    album_id BIGINT REFERENCES album(album_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE performer_song
(
    performer_id BIGINT         NOT NULL REFERENCES performer (performer_id) ON DELETE CASCADE,
    song_id      BIGINT         NOT NULL REFERENCES song (song_id) ON DELETE CASCADE,
    role         performer_role NOT NULL DEFAULT 'main',
    PRIMARY KEY (performer_id, song_id)
);

CREATE TABLE song_genre
(
    song_id    BIGINT      NOT NULL REFERENCES song (song_id) ON DELETE CASCADE,
    genre_name VARCHAR(64) NOT NULL REFERENCES genre (genre_name),
    PRIMARY KEY (song_id, genre_name)
);

CREATE TABLE listener_preferred_genre
(
    listener_id BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    genre_name  VARCHAR(64) NOT NULL REFERENCES genre (genre_name),
    PRIMARY KEY (listener_id, genre_name)
);

CREATE TABLE listener_genre_priority
(
    listener_id    BIGINT         NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    genre_name     VARCHAR(64)    NOT NULL REFERENCES genre (genre_name),
    priority_score NUMERIC(12, 6) NOT NULL DEFAULT 0 CHECK (priority_score >= 0),
    last_updated   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, genre_name)
);

CREATE TABLE listener_following_performer
(
    listener_id  BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    performer_id BIGINT      NOT NULL REFERENCES performer (performer_id) ON DELETE CASCADE,
    followed_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, performer_id)
);

CREATE TABLE listener_performer_attitude
(
    listener_id  BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    performer_id BIGINT      NOT NULL REFERENCES performer (performer_id) ON DELETE CASCADE,
    attitude     attitude    NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, performer_id)
);

CREATE TABLE song_stream
(
    stream_id   BIGSERIAL PRIMARY KEY,
    streamed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    skipped     BOOLEAN     NOT NULL DEFAULT FALSE,
    listener_id BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    song_id     BIGINT      NOT NULL REFERENCES song (song_id) ON DELETE CASCADE
);

CREATE TABLE listener_song_activity
(
    listener_id  BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    song_id      BIGINT      NOT NULL REFERENCES song (song_id) ON DELETE CASCADE,
    stream_count INTEGER     NOT NULL DEFAULT 0 CHECK (stream_count >= 0),
    skip_count   INTEGER     NOT NULL DEFAULT 0 CHECK (skip_count >= 0),
    attitude     attitude,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, song_id)
);

CREATE TABLE blocked_song
(
    listener_id BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    song_id     BIGINT      NOT NULL REFERENCES song (song_id) ON DELETE CASCADE,
    blocked_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, song_id)
);

CREATE TABLE blocked_performer
(
    listener_id  BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    performer_id BIGINT      NOT NULL REFERENCES performer (performer_id) ON DELETE CASCADE,
    blocked_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, performer_id)
);

CREATE TABLE playlist
(
    playlist_id   BIGSERIAL PRIMARY KEY,
    playlist_name VARCHAR(128)  NOT NULL,
    type          playlist_type NOT NULL,
    playlist_url  TEXT CHECK (playlist_url IS NULL OR playlist_url ~* '^https?://'
) ,
    picture_url TEXT CHECK (picture_url IS NULL OR picture_url ~* '^https?://'),
    creator_id BIGINT NOT NULL REFERENCES listener(listener_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE playlist_member
(
    listener_id BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    playlist_id BIGINT      NOT NULL REFERENCES playlist (playlist_id) ON DELETE CASCADE,
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, playlist_id)
);

CREATE TABLE playlist_song
(
    playlist_id          BIGINT      NOT NULL REFERENCES playlist (playlist_id) ON DELETE CASCADE,
    song_id              BIGINT      NOT NULL REFERENCES song (song_id) ON DELETE CASCADE,
    added_by_listener_id BIGINT      REFERENCES listener (listener_id) ON DELETE SET NULL,
    added_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (playlist_id, song_id)
);

CREATE TABLE playlist_song_vote
(
    playlist_id BIGINT      NOT NULL,
    song_id     BIGINT      NOT NULL,
    listener_id BIGINT      NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    vote        SMALLINT    NOT NULL DEFAULT 1 CHECK (vote = 1),
    voted_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (playlist_id, song_id, listener_id),
    FOREIGN KEY (playlist_id, song_id) REFERENCES playlist_song (playlist_id, song_id) ON DELETE CASCADE
);

CREATE TABLE listener_recommendation
(
    listener_id          BIGINT         NOT NULL REFERENCES listener (listener_id) ON DELETE CASCADE,
    song_id              BIGINT         NOT NULL REFERENCES song (song_id) ON DELETE CASCADE,
    recommendation_score NUMERIC(10, 4) NOT NULL CHECK (recommendation_score >= 0),
    generated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listener_id, song_id)
);

CREATE INDEX idx_listener_plan_start_date
    ON listener (plan_name, plan_start_date) WHERE plan_name != 'free';

CREATE INDEX idx_performer_type
    ON performer (performer_type);

CREATE INDEX idx_album_performer
    ON album (performer_id);

CREATE INDEX idx_song_main_performer
    ON song (main_performer_id);

CREATE INDEX idx_song_release_date
    ON song (release_date DESC);

CREATE INDEX idx_song_genre_genre
    ON song_genre (genre_name);

CREATE INDEX idx_listener_genre_priority_score
    ON listener_genre_priority (listener_id, priority_score DESC);

CREATE INDEX idx_song_stream_listener_song
    ON song_stream (listener_id, song_id);

CREATE INDEX idx_song_stream_streamed_at
    ON song_stream (streamed_at DESC);

CREATE INDEX idx_listener_song_activity_attitude
    ON listener_song_activity (listener_id, attitude);

CREATE INDEX idx_playlist_creator
    ON playlist (creator_id);

CREATE INDEX idx_playlist_type
    ON playlist (type);

CREATE INDEX idx_playlist_song_vote_total
    ON playlist_song_vote (playlist_id, song_id);

CREATE INDEX idx_listener_recommendation_score
    ON listener_recommendation (listener_id, recommendation_score DESC);
