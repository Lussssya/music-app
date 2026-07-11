import { FormEvent, useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { getAlbums, getGenres, getPerformers, getSongs } from '../api/client';
import type { Album, Genre, Performer, Song } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';

type CatalogView = 'songs' | 'performers' | 'albums' | 'genres';

export function CatalogScreen() {
  const [view, setView] = useState<CatalogView>('songs');
  const [search, setSearch] = useState('');
  const [genreName, setGenreName] = useState('');
  const [performerId, setPerformerId] = useState('');
  const [songs, setSongs] = useState<Song[]>([]);
  const [performers, setPerformers] = useState<Performer[]>([]);
  const [albums, setAlbums] = useState<Album[]>([]);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loadCatalog();
  }, []);

  async function loadCatalog(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (view === 'songs') {
        const params = new URLSearchParams();
        if (search.trim()) {
          params.set('search', search.trim());
        }
        if (genreName.trim()) {
          params.set('genreName', genreName.trim());
        }
        if (performerId.trim()) {
          params.set('performerId', performerId.trim());
        }
        setSongs(await getSongs(params));
      }
      if (view === 'performers') {
        setPerformers(await getPerformers(search));
      }
      if (view === 'albums') {
        setAlbums(await getAlbums(search, performerId));
      }
      if (view === 'genres') {
        setGenres(await getGenres());
      }
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="screen-stack">
      <div className="panel">
        <h2>Catalog</h2>
        <form className="toolbar-form" onSubmit={loadCatalog}>
          <div className="segmented-control wide" aria-label="Catalog view">
            {(['songs', 'performers', 'albums', 'genres'] as CatalogView[]).map((catalogView) => (
              <button key={catalogView} type="button" className={view === catalogView ? 'active' : ''} onClick={() => setView(catalogView)}>
                {catalogView}
              </button>
            ))}
          </div>

          {view !== 'genres' && (
            <input placeholder="Search" value={search} onChange={(event) => setSearch(event.target.value)} />
          )}
          {view === 'songs' && (
            <input placeholder="Genre name" value={genreName} onChange={(event) => setGenreName(event.target.value)} />
          )}
          {(view === 'songs' || view === 'albums') && (
            <input placeholder="Performer ID" value={performerId} onChange={(event) => setPerformerId(event.target.value)} />
          )}

          <button className="primary-action compact" type="submit" disabled={loading}>
            <Search size={18} />
            {loading ? 'Loading' : 'Load'}
          </button>
        </form>
        <StatusMessage error={error} message={null} />
      </div>

      {view === 'songs' && (
        <div className="result-grid">
          {songs.map((song) => (
            <article className="result-card" key={song.songId}>
              <h3>{song.title}</h3>
              <p>{song.mainPerformer.nickname} · {song.genres.join(', ') || 'No genres'}</p>
              <span>ID {song.songId}</span>
            </article>
          ))}
          {!songs.length && <EmptyState>No songs loaded yet.</EmptyState>}
        </div>
      )}

      {view === 'performers' && (
        <div className="result-grid">
          {performers.map((performer) => (
            <article className="result-card" key={performer.performerId}>
              <h3>{performer.nickname}</h3>
              <p>{performer.performerType}{performer.verified ? ' · verified' : ''}</p>
              <span>ID {performer.performerId}</span>
            </article>
          ))}
          {!performers.length && <EmptyState>No performers loaded yet.</EmptyState>}
        </div>
      )}

      {view === 'albums' && (
        <div className="result-grid">
          {albums.map((album) => (
            <article className="result-card" key={album.albumId}>
              <h3>{album.albumName}</h3>
              <p>{album.performer.nickname} · {album.releaseDate}</p>
              <span>ID {album.albumId}</span>
            </article>
          ))}
          {!albums.length && <EmptyState>No albums loaded yet.</EmptyState>}
        </div>
      )}

      {view === 'genres' && (
        <div className="chip-grid">
          {genres.map((genre) => <span key={genre.genreName}>{genre.genreName}</span>)}
          {!genres.length && <EmptyState>No genres loaded yet.</EmptyState>}
        </div>
      )}
    </div>
  );
}
