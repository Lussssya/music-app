import { FormEvent, useState } from 'react';
import { ListPlus, Plus, RefreshCw, ThumbsUp, Trash2, UserPlus, UserX } from 'lucide-react';
import {
  addPlaylistSong,
  createPlaylist,
  getPlaylist,
  getPlaylists,
  joinPlaylist,
  leavePlaylist,
  removePlaylistSong,
  setPlaylistSongVote
} from '../api/client';
import type { AuthSession, Playlist, PlaylistRequest, PlaylistSummary } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';

type PlaylistsScreenProps = {
  session: AuthSession;
};

const initialCreateForm: PlaylistRequest = {
  name: 'Manual Test Playlist',
  type: 'private',
  playlistUrl: null,
  pictureUrl: null
};

export function PlaylistsScreen({ session }: PlaylistsScreenProps) {
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [selectedPlaylist, setSelectedPlaylist] = useState<Playlist | null>(null);
  const [playlistId, setPlaylistId] = useState('1');
  const [songId, setSongId] = useState('10');
  const [createForm, setCreateForm] = useState<PlaylistRequest>(initialCreateForm);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function run<T>(action: () => Promise<T>, onSuccess: (response: T) => void, successMessage: string) {
    setError(null);
    setMessage(null);
    try {
      onSuccess(await action());
      setMessage(successMessage);
    } catch (caughtError) {
      setError(displayError(caughtError));
    }
  }

  async function loadPlaylists(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    const params = new URLSearchParams();
    if (search.trim()) {
      params.set('search', search.trim());
    }
    if (type.trim()) {
      params.set('type', type.trim());
    }

    await run(() => getPlaylists(params), setPlaylists, 'Playlists loaded.');
  }

  return (
    <div className="screen-stack">
      <StatusMessage error={error} message={message} />

      <div className="panel">
        <h2>Playlists</h2>
        <form className="toolbar-form" onSubmit={loadPlaylists}>
          <input placeholder="Search" value={search} onChange={(event) => setSearch(event.target.value)} />
          <select value={type} onChange={(event) => setType(event.target.value)}>
            <option value="">Any type</option>
            <option value="private">Private</option>
            <option value="public">Public</option>
            <option value="shared">Shared</option>
          </select>
          <button className="primary-action compact" type="submit"><RefreshCw size={16} /> Load</button>
        </form>
      </div>

      <div className="split-layout">
        <div className="result-grid">
          {playlists.map((playlist) => (
            <article className="result-card" key={playlist.playlistId}>
              <h3>{playlist.name}</h3>
              <p>{playlist.type} · {playlist.songCount} songs · {playlist.memberCount} members</p>
              <button type="button" onClick={() => run(() => getPlaylist(String(playlist.playlistId)), setSelectedPlaylist, 'Playlist details loaded.')}>
                Open ID {playlist.playlistId}
              </button>
            </article>
          ))}
          {!playlists.length && <EmptyState>No playlists loaded yet.</EmptyState>}
        </div>

        <div className="panel action-panel">
          <h2>Selected Playlist</h2>
          <div className="toolbar-form">
            <input value={playlistId} onChange={(event) => setPlaylistId(event.target.value)} placeholder="Playlist ID" />
            <button type="button" onClick={() => run(() => getPlaylist(playlistId), setSelectedPlaylist, 'Playlist details loaded.')}>Load by ID</button>
            <button type="button" onClick={() => run(() => joinPlaylist(session, playlistId), setSelectedPlaylist, 'Joined playlist.')}><UserPlus size={16} /> Join</button>
            <button type="button" onClick={() => run(() => leavePlaylist(session, playlistId), setSelectedPlaylist, 'Left playlist.')}><UserX size={16} /> Leave</button>
          </div>

          <div className="toolbar-form">
            <input value={songId} onChange={(event) => setSongId(event.target.value)} placeholder="Song ID" />
            <button type="button" onClick={() => run(() => addPlaylistSong(session, playlistId, songId), setSelectedPlaylist, 'Song added.')}><Plus size={16} /> Add song</button>
            <button type="button" onClick={() => run(() => removePlaylistSong(session, playlistId, songId), setSelectedPlaylist, 'Song removed.')}><Trash2 size={16} /> Remove song</button>
            <button type="button" onClick={() => run(() => setPlaylistSongVote(session, playlistId, songId, true), setSelectedPlaylist, 'Vote added.')}><ThumbsUp size={16} /> Vote</button>
            <button type="button" onClick={() => run(() => setPlaylistSongVote(session, playlistId, songId, false), setSelectedPlaylist, 'Vote removed.')}>Unvote</button>
          </div>

          {selectedPlaylist && (
            <div className="details-block">
              <h3>{selectedPlaylist.name}</h3>
              <p>{selectedPlaylist.type} · created by {selectedPlaylist.creatorUsername}</p>
              <h4>Songs</h4>
              {selectedPlaylist.songs.map((song) => (
                <span key={song.songId}>{song.songId}. {song.title} · votes {song.voteCount}</span>
              ))}
              {!selectedPlaylist.songs.length && <p>No songs in this playlist yet.</p>}
            </div>
          )}
        </div>
      </div>

      <div className="panel">
        <h2>Create Playlist</h2>
        <form className="toolbar-form" onSubmit={(event) => {
          event.preventDefault();
          void run(() => createPlaylist(session, createForm), setSelectedPlaylist, 'Playlist created.');
        }}>
          <input value={createForm.name} onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })} placeholder="Name" />
          <select value={createForm.type} onChange={(event) => setCreateForm({ ...createForm, type: event.target.value })}>
            <option value="private">Private</option>
            <option value="public">Public</option>
            <option value="shared">Shared</option>
          </select>
          <button className="primary-action compact" type="submit"><ListPlus size={16} /> Create</button>
        </form>
      </div>
    </div>
  );
}
