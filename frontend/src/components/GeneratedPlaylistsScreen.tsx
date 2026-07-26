import { useEffect, useState } from 'react';
import { Heart, ListPlus, Play, Save, Sparkles } from 'lucide-react';
import { addPlaylistSong, createPlaylist, generatePlaylist, getGeneratedPlaylists } from '../api/client';
import type { AuthSession, GeneratedPlaylist, GeneratedPlaylistSummary, Song } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';
import { useLibrary } from './LibraryProvider';
import { usePlayer } from './PlayerProvider';

type GeneratedPlaylistsScreenProps = {
  session: AuthSession;
};

const cardColors = ['mint', 'violet', 'coral', 'sky', 'gold', 'plum', 'teal', 'rose', 'blue'];

export function GeneratedPlaylistsScreen({ session }: GeneratedPlaylistsScreenProps) {
  const { playNow, playQueue, addToQueue } = usePlayer();
  const { isFavorite, toggleFavorite } = useLibrary();
  const [playlists, setPlaylists] = useState<GeneratedPlaylistSummary[]>([]);
  const [selected, setSelected] = useState<GeneratedPlaylist | null>(null);
  const [loadingType, setLoadingType] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadPlaylists();
  }, [session]);

  useEffect(() => {
    const nextExpiry = playlists
      .filter((playlist) => playlist.available && playlist.expiresAt)
      .map((playlist) => new Date(playlist.expiresAt!).getTime())
      .sort((first, second) => first - second)[0];

    if (!nextExpiry) {
      return;
    }

    const timer = window.setTimeout(() => void loadPlaylists(), Math.max(nextExpiry - Date.now() + 1_000, 1_000));
    return () => window.clearTimeout(timer);
  }, [playlists]);

  async function loadPlaylists() {
    setError(null);
    try {
      const loadedPlaylists = await getGeneratedPlaylists(session);
      setPlaylists(loadedPlaylists);
      setSelected((current) => current && loadedPlaylists.some((playlist) => playlist.type === current.type && playlist.available) ? current : null);
    } catch (caughtError) {
      setError(displayError(caughtError));
    }
  }

  async function openPlaylist(playlist: GeneratedPlaylistSummary) {
    setError(null);
    setLoadingType(playlist.type);
    try {
      const generatedPlaylist = await generatePlaylist(session, playlist.type);
      setSelected(generatedPlaylist);
      setPlaylists((current) => current.map((item) => item.type === generatedPlaylist.type ? generatedPlaylist : item));
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setLoadingType(null);
    }
  }

  function playSong(song: Song) {
    const songs = selected?.songs ?? [];
    playNow(song, songs.slice(songs.indexOf(song) + 1));
  }

  async function saveAsPlaylist() {
    if (!selected) return;
    setError(null); setSaving(true);
    try {
      const playlist = await createPlaylist(session, { name: selected.name, type: 'private', playlistUrl: null, pictureUrl: null });
      await Promise.all(selected.songs.map((song) => addPlaylistSong(session, String(playlist.playlistId), String(song.songId))));
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="screen-stack generated-screen">
      <StatusMessage error={error} message={null} />
      <section className="generated-hero">
        <div className="generated-hero-icon"><Sparkles size={30} /></div>
        <div>
          <p className="eyebrow">Made for {session.user.username}</p>
          <h2>Your generated playlists</h2>
          <p>Fresh mixes shaped by what you play, love, skip, and revisit.</p>
        </div>
      </section>

      <section>
        <div className="generated-section-heading">
          <div>
            <h2>Pick up where your taste leads</h2>
            <p>Open a playlist to generate its current tracklist.</p>
          </div>
          <button type="button" className="text-button" onClick={() => void loadPlaylists()}>Refresh</button>
        </div>
        <GeneratedPlaylistCards playlists={playlists.filter((playlist) => playlist.available)} loadingType={loadingType} onOpen={openPlaylist} label="Ready to play" />
        <GeneratedPlaylistCards playlists={playlists.filter((playlist) => !playlist.available)} loadingType={loadingType} onOpen={openPlaylist} label="Create a fresh mix" />
        {!playlists.length && !error && <EmptyState>Your generated playlists will appear here.</EmptyState>}
      </section>

      {selected && (
        <section className="generated-detail">
          <div className="generated-detail-heading">
            <div>
              <p className="eyebrow">Generated playlist</p>
              <h2>{selected.name}</h2>
              <p>{selected.description} · {selected.songs.length} songs</p>
            </div>
            {!!selected.songs.length && <div className="generated-detail-actions"><button type="button" onClick={() => void saveAsPlaylist()} disabled={saving}><Save size={17} /> {saving ? 'Saving…' : 'Save playlist'}</button><button className="primary-action" type="button" onClick={() => playQueue(selected.songs)}><Play size={17} fill="currentColor" /> Play</button></div>}
          </div>
          {selected.songs.length ? (
            <ol className="generated-song-list">
              {selected.songs.map((song, index) => (
                <li key={song.songId}>
                  <span className="song-number">{index + 1}</span>
                  <div><strong>{song.title}</strong><span>{song.mainPerformer.nickname} · {song.album?.albumName ?? 'Single'}</span></div>
                  <div className="song-list-actions">
                    <button type="button" aria-label={`Play ${song.title}`} onClick={() => playSong(song)}><Play size={16} fill="currentColor" /></button>
                    <button type="button" aria-label={`Add ${song.title} to queue`} onClick={() => addToQueue(song)}><ListPlus size={17} /></button>
                    <button className={isFavorite(song.songId) ? 'favorite active' : 'favorite'} type="button" aria-label={isFavorite(song.songId) ? `Remove ${song.title} from favorites` : `Add ${song.title} to favorites`} onClick={() => void toggleFavorite(song)}><Heart size={16} fill={isFavorite(song.songId) ? 'currentColor' : 'none'} /></button>
                  </div>
                </li>
              ))}
            </ol>
          ) : <EmptyState>Not enough listening data for this playlist yet. Play more music and try again.</EmptyState>}
        </section>
      )}
    </div>
  );
}

type GeneratedPlaylistCardsProps = {
  playlists: GeneratedPlaylistSummary[];
  loadingType: string | null;
  label: string;
  onOpen: (playlist: GeneratedPlaylistSummary) => Promise<void>;
};

function GeneratedPlaylistCards({ playlists, loadingType, label, onOpen }: GeneratedPlaylistCardsProps) {
  if (!playlists.length) {
    return null;
  }

  return (
    <div className="generated-playlist-group">
      <h3>{label}</h3>
      <div className="generated-grid">
        {playlists.map((playlist, index) => (
            <button
              className="generated-card"
              type="button"
              key={playlist.type}
              onClick={() => void onOpen(playlist)}
              disabled={loadingType !== null}
            >
              <span className={`generated-cover ${cardColors[index % cardColors.length]}`}><Sparkles size={26} /></span>
              <strong>{playlist.name}</strong>
              <span>{loadingType === playlist.type ? 'Building your playlist…' : playlist.available ? `Available until ${new Date(playlist.expiresAt!).toLocaleString()}` : playlist.description}</span>
            </button>
          ))}
      </div>
    </div>
  );
}
