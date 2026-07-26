import { FormEvent, useMemo, useState } from 'react';
import { Check, ListPlus, Pencil, Play, Plus, RefreshCw, Search, Trash2, UserPlus, UserX, X } from 'lucide-react';
import {
  addPlaylistMember, addPlaylistSong, createPlaylist, deletePlaylist, getPlaylist, getPlaylists, joinPlaylist,
  getSong, leavePlaylist, removePlaylistMember, removePlaylistSong, updatePlaylist
} from '../api/client';
import type { AuthSession, Playlist, PlaylistRequest, PlaylistSummary } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';
import { usePlayer } from './PlayerProvider';

type PlaylistsScreenProps = { session: AuthSession };

const coverOptions = [
  'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=600&q=80'
];

const emptyForm: PlaylistRequest = { name: '', type: 'private', playlistUrl: null, pictureUrl: coverOptions[0] };

export function PlaylistsScreen({ session }: PlaylistsScreenProps) {
  const { playQueue } = usePlayer();
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [selected, setSelected] = useState<Playlist | null>(null);
  const [form, setForm] = useState<PlaylistRequest>(emptyForm);
  const [editing, setEditing] = useState(false);
  const [songId, setSongId] = useState('');
  const [memberUsername, setMemberUsername] = useState('');
  const [songSearch, setSongSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const isCreator = selected?.creatorId === session.user.listenerId;
  const isMember = selected?.members.some((member) => member.listenerId === session.user.listenerId) ?? false;
  const visibleSongs = useMemo(() => selected?.songs.filter((song) => `${song.title} ${song.mainPerformerName}`.toLowerCase().includes(songSearch.trim().toLowerCase())) ?? [], [selected, songSearch]);

  async function run<T>(action: () => Promise<T>, onSuccess: (result: T) => void, successMessage: string) {
    setError(null); setMessage(null);
    try { onSuccess(await action()); setMessage(successMessage); } catch (caught) { setError(displayError(caught)); }
  }

  function selectPlaylist(playlist: Playlist) {
    setSelected(playlist); setEditing(false); setSongSearch('');
    setForm({ name: playlist.name, type: playlist.type, playlistUrl: playlist.playlistUrl, pictureUrl: playlist.pictureUrl });
  }

  async function loadPlaylists(event?: FormEvent) {
    event?.preventDefault();
    const params = new URLSearchParams();
    if (search.trim()) params.set('search', search.trim());
    if (type) params.set('type', type);
    await run(() => getPlaylists(params), setPlaylists, 'Playlists loaded.');
  }

  async function savePlaylist(event: FormEvent) {
    event.preventDefault();
    if (editing && selected) {
      await run(() => updatePlaylist(session, String(selected.playlistId), form), selectPlaylist, 'Playlist updated.');
    } else {
      await run(() => createPlaylist(session, form), (playlist) => { selectPlaylist(playlist); setEditing(true); }, 'Playlist created.');
    }
  }

  async function playSelectedPlaylist() {
    if (!selected?.songs.length) return;
    setError(null);
    try { playQueue(await Promise.all(selected.songs.map((song) => getSong(String(song.songId))))); }
    catch (caught) { setError(displayError(caught)); }
  }

  return <div className="screen-stack playlist-screen">
    <StatusMessage error={error} message={message} />
    <section className="panel playlist-browser">
      <div className="catalog-heading"><div><h2>Your playlists</h2><p>Build a personal collection or collaborate with friends.</p></div><button className="primary-action" type="button" onClick={() => { setSelected(null); setForm(emptyForm); setEditing(false); }}> <ListPlus size={16} /> New playlist</button></div>
      <form className="toolbar-form" onSubmit={loadPlaylists}>
        <input placeholder="Search playlists" value={search} onChange={(event) => setSearch(event.target.value)} />
        <select value={type} onChange={(event) => setType(event.target.value)}><option value="">Any visibility</option><option value="private">Private</option><option value="public">Public</option><option value="shared">Shared</option></select>
        <button type="submit"><RefreshCw size={16} /> Load</button>
      </form>
    </section>

    <div className="playlist-workspace">
      <section className="playlist-cards">
        {playlists.map((playlist) => <button className={`playlist-card ${selected?.playlistId === playlist.playlistId ? 'active' : ''}`} key={playlist.playlistId} type="button" onClick={() => void run(() => getPlaylist(String(playlist.playlistId)), selectPlaylist, 'Playlist opened.')}>
          {playlist.pictureUrl ? <img src={playlist.pictureUrl} alt="" /> : <span className="playlist-cover-fallback">♫</span>}
          <span><strong>{playlist.name}</strong><small>{playlist.type} · {playlist.songCount} songs · {playlist.memberCount} members</small></span>
        </button>)}
        {!playlists.length && <EmptyState>Load or search for playlists to start.</EmptyState>}
      </section>

      <section className="panel playlist-editor">
        <form className="playlist-form" onSubmit={savePlaylist}>
          <div className="playlist-form-heading"><div><p className="eyebrow">{editing ? 'Edit playlist' : 'New playlist'}</p><h2>{editing ? form.name || 'Untitled playlist' : 'Create a playlist'}</h2></div>{editing && <button type="button" className="text-button" onClick={() => { setSelected(null); setForm(emptyForm); setEditing(false); }}>Cancel</button>}</div>
          <label>Name<input required maxLength={128} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Playlist name" /></label>
          <label>Visibility<select value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}><option value="private">Private</option><option value="public">Public</option><option value="shared">Shared</option></select></label>
          <label>Playlist link (optional)<input value={form.playlistUrl ?? ''} onChange={(event) => setForm({ ...form, playlistUrl: event.target.value || null })} placeholder="https://…" /></label>
          <div><span className="field-label">Cover artwork</span><div className="cover-picker">{coverOptions.map((cover) => <button key={cover} className={form.pictureUrl === cover ? 'selected' : ''} type="button" onClick={() => setForm({ ...form, pictureUrl: cover })}><img src={cover} alt="Select this cover" />{form.pictureUrl === cover && <Check size={16} />}</button>)}</div></div>
          <label>Or use an image URL<input value={form.pictureUrl ?? ''} onChange={(event) => setForm({ ...form, pictureUrl: event.target.value || null })} placeholder="https://…" /></label>
          <button className="primary-action" type="submit">{editing ? <><Pencil size={16} /> Save changes</> : <><ListPlus size={16} /> Create playlist</>}</button>
        </form>

        {selected && <div className="playlist-detail">
          <div className="playlist-detail-hero">{selected.pictureUrl ? <img src={selected.pictureUrl} alt="" /> : <span className="playlist-cover-fallback">♫</span>}<div><p className="eyebrow">{selected.type} playlist</p><h2>{selected.name}</h2><p>Created by {selected.creatorUsername} · {selected.songs.length} songs</p></div></div>
          <div className="playlist-actions">{!!selected.songs.length && <button className="primary-action" type="button" onClick={() => void playSelectedPlaylist()}><Play size={15} /> Play all</button>}{isCreator ? <><button type="button" onClick={() => setEditing(true)}><Pencil size={15} /> Edit</button><button className="danger-action" type="button" onClick={() => void run(() => deletePlaylist(session, String(selected.playlistId)), () => { setSelected(null); setEditing(false); }, 'Playlist deleted.')}><Trash2 size={15} /> Delete</button></> : isMember ? <button type="button" onClick={() => void run(() => leavePlaylist(session, String(selected.playlistId)), selectPlaylist, 'Left playlist.')}><UserX size={15} /> Leave</button> : <button type="button" onClick={() => void run(() => joinPlaylist(session, String(selected.playlistId)), selectPlaylist, 'Joined playlist.')}><UserPlus size={15} /> Join</button>}</div>
          {isMember && <div className="playlist-add-song"><input value={songId} onChange={(event) => setSongId(event.target.value)} placeholder="Song ID" /><button type="button" onClick={() => void run(() => addPlaylistSong(session, String(selected.playlistId), songId), selectPlaylist, 'Song added.')}><Plus size={15} /> Add song</button></div>}
          <div className="playlist-songs-heading"><h3>Songs</h3><label className="song-search"><Search size={15} /><input value={songSearch} onChange={(event) => setSongSearch(event.target.value)} placeholder="Search within playlist" /></label></div>
          <div className="playlist-song-list">{visibleSongs.map((song, index) => <div key={song.songId}><span>{index + 1}</span><p><strong>{song.title}</strong><small>{song.mainPerformerName} · {song.voteCount} votes</small></p>{isMember && <button type="button" aria-label={`Remove ${song.title}`} onClick={() => void run(() => removePlaylistSong(session, String(selected.playlistId), String(song.songId)), selectPlaylist, 'Song removed.')}><X size={16} /></button>}</div>)}{!visibleSongs.length && <EmptyState>No songs match this search.</EmptyState>}</div>
          <div className="playlist-members"><h3>Collaborators</h3>{isCreator && <div className="playlist-add-song"><input value={memberUsername} onChange={(event) => setMemberUsername(event.target.value)} placeholder="Username" /><button type="button" onClick={() => void run(() => addPlaylistMember(session, String(selected.playlistId), memberUsername), selectPlaylist, 'Collaborator added.')}><UserPlus size={15} /> Invite</button></div>}<div className="member-list">{selected.members.map((member) => <span key={member.listenerId}>{member.username}{isCreator && member.listenerId !== selected.creatorId && <button type="button" aria-label={`Remove ${member.username}`} onClick={() => void run(() => removePlaylistMember(session, String(selected.playlistId), member.username), selectPlaylist, 'Collaborator removed.')}><X size={14} /></button>}</span>)}</div></div>
        </div>}
      </section>
    </div>
  </div>;
}
