-- Public demo audio makes the seeded catalog playable in local and demo environments.
UPDATE song
SET song_url = 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-' || (((song_id - 1) % 8) + 1) || '.mp3';
