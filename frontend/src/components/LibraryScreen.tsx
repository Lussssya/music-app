import { Heart, ListPlus, Play, RefreshCw } from 'lucide-react';
import { useLibrary } from './LibraryProvider';
import { usePlayer } from './PlayerProvider';
import { EmptyState, StatusMessage } from './ScreenHelpers';

export function LibraryScreen() {
  const { favorites, loading, error, toggleFavorite, refreshLibrary } = useLibrary();
  const { playNow, addToQueue } = usePlayer();

  function playLibrary() {
    const [first, ...rest] = favorites;
    if (first) playNow(first, rest);
  }

  return (
    <div className="screen-stack">
      <div className="panel library-hero">
        <div>
          <p className="eyebrow">Your collection</p>
          <h2>Favorites</h2>
          <p>Tracks you love, ready to play whenever you come back.</p>
        </div>
        <div className="library-summary">
          <strong>{favorites.length}</strong>
          <span>{favorites.length === 1 ? 'saved track' : 'saved tracks'}</span>
        </div>
        <div className="library-actions">
          <button className="primary-action compact" type="button" onClick={playLibrary} disabled={!favorites.length}><Play size={17} /> Play all</button>
          <button type="button" onClick={() => favorites.forEach(addToQueue)} disabled={!favorites.length}><ListPlus size={17} /> Queue all</button>
          <button type="button" onClick={() => void refreshLibrary()} disabled={loading}><RefreshCw size={17} /> Refresh</button>
        </div>
        <StatusMessage error={error} message={null} />
      </div>

      <div className="library-list">
        {favorites.map((song, index) => (
          <article className="library-row" key={song.songId}>
            <span className="track-number">{String(index + 1).padStart(2, '0')}</span>
            <button className="library-track" type="button" onClick={() => playNow(song, favorites.slice(index + 1))}>
              <strong>{song.title}</strong>
              <span>{song.mainPerformer.nickname} · {song.album?.albumName ?? 'Single'}</span>
            </button>
            <span className="library-genres">{song.genres.slice(0, 2).join(' · ')}</span>
            <button className="icon-action favorite active" type="button" aria-label={`Remove ${song.title} from favorites`} onClick={() => void toggleFavorite(song)}><Heart size={18} fill="currentColor" /></button>
            <button className="icon-action" type="button" aria-label={`Add ${song.title} to queue`} onClick={() => addToQueue(song)}><ListPlus size={18} /></button>
          </article>
        ))}
        {!loading && !favorites.length && <EmptyState>Your library is waiting. Heart a track in Catalog or Recommendations to save it here.</EmptyState>}
        {loading && <EmptyState>Loading your library…</EmptyState>}
      </div>
    </div>
  );
}
