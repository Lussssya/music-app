-- V2 inserts fixed IDs for demo data. Advance the corresponding sequences so
-- newly created records do not collide with the seeded rows.
SELECT setval(pg_get_serial_sequence('performer', 'performer_id'), (SELECT MAX(performer_id) FROM performer));
SELECT setval(pg_get_serial_sequence('listener', 'listener_id'), (SELECT MAX(listener_id) FROM listener));
SELECT setval(pg_get_serial_sequence('album', 'album_id'), (SELECT MAX(album_id) FROM album));
SELECT setval(pg_get_serial_sequence('song', 'song_id'), (SELECT MAX(song_id) FROM song));
SELECT setval(pg_get_serial_sequence('playlist', 'playlist_id'), (SELECT MAX(playlist_id) FROM playlist));
