import { FormEvent, useEffect, useState } from 'react';
import { Heart, ListPlus, Play, RotateCcw, Search } from 'lucide-react';
import { getAlbums, getGenres, getPerformers, getSongs } from '../api/client';
import type { Album, Genre, Performer, Song } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';
import { usePlayer } from './PlayerProvider';
import { useLibrary } from './LibraryProvider';

type CatalogView = 'songs' | 'performers' | 'albums' | 'genres';

export function CatalogScreen() {
  const { playNow, addToQueue } = usePlayer();
  const { isFavorite, toggleFavorite } = useLibrary();
  const [view, setView] = useState<CatalogView>('songs');
  const [search, setSearch] = useState('');
  const [genreName, setGenreName] = useState('');
  const [performerId, setPerformerId] = useState('');
  const [songs, setSongs] = useState<Song[]>([]);
  const [performers, setPerformers] = useState<Performer[]>([]);
  const [albums, setAlbums] = useState<Album[]>([]);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [filterPerformers, setFilterPerformers] = useState<Performer[]>([]);
  const [filterGenres, setFilterGenres] = useState<Genre[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loadFilterOptions();
  }, []);

  useEffect(() => {
    void loadCatalog();
  }, [view]);

  async function loadFilterOptions() {
    try {
      const [availablePerformers, availableGenres] = await Promise.all([getPerformers(''), getGenres()]);
      setFilterPerformers(availablePerformers);
      setFilterGenres(availableGenres);
    } catch (caughtError) {
      setError(displayError(caughtError));
    }
  }

  async function loadCatalog(event?: FormEvent<HTMLFormElement>, reset = false) {
    event?.preventDefault();
    setError(null);
    setLoading(true);

    const activeSearch = reset ? '' : search.trim();
    const activeGenre = reset ? '' : genreName;
    const activePerformer = reset ? '' : performerId;

    try {
      if (view === 'songs') {
        const params = new URLSearchParams();
        if (activeSearch) params.set('search', activeSearch);
        if (activeGenre) params.set('genreName', activeGenre);
        if (activePerformer) params.set('performerId', activePerformer);
        setSongs(await getSongs(params));
      } else if (view === 'performers') {
        setPerformers(await getPerformers(activeSearch));
      } else if (view === 'albums') {
        setAlbums(await getAlbums(activeSearch, activePerformer));
      } else {
        setGenres(await getGenres());
      }
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setLoading(false);
    }
  }

  function clearFilters() {
    setSearch('');
    setGenreName('');
    setPerformerId('');
    void loadCatalog(undefined, true);
  }

  const visibleGenres = genres.filter((genre) =>
    genre.genreName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())
  );
  const resultCount = view === 'songs'
    ? songs.length
    : view === 'performers'
      ? performers.length
      : view === 'albums'
        ? albums.length
        : visibleGenres.length;
  const hasFilters = Boolean(search.trim() || genreName || performerId);

  return (
    <div className="screen-stack">
      <div className="panel catalog-panel">
        <div className="catalog-heading">
          <div>
            <h2>Search and filtering</h2>
            <p>Find music by title, artist, album, or genre.</p>
          </div>
          <span className="result-count" aria-live="polite">{loading ? 'Searching…' : `${resultCount} results`}</span>
        </div>

        <form className="toolbar-form catalog-filters" onSubmit={loadCatalog}>
          <div className="segmented-control wide" aria-label="Catalog view">
            {(['songs', 'performers', 'albums', 'genres'] as CatalogView[]).map((catalogView) => (
              <button key={catalogView} type="button" className={view === catalogView ? 'active' : ''} onClick={() => setView(catalogView)}>
                {catalogView}
              </button>
            ))}
          </div>

          <label className="search-field">
            <span>{view === 'genres' ? 'Genre name' : 'Search'}</span>
            <input
              aria-label={view === 'genres' ? 'Genre name' : 'Search catalog'}
              placeholder={view === 'genres' ? 'e.g. Pop' : `Search ${view}`}
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>

          {(view === 'songs' || view === 'albums') && (
            <label>
              <span>Performer</span>
              <select aria-label="Filter by performer" value={performerId} onChange={(event) => setPerformerId(event.target.value)}>
                <option value="">All performers</option>
                {filterPerformers.map((performer) => (
                  <option key={performer.performerId} value={performer.performerId}>{performer.nickname}</option>
                ))}
              </select>
            </label>
          )}

          {view === 'songs' && (
            <label>
              <span>Genre</span>
              <select aria-label="Filter by genre" value={genreName} onChange={(event) => setGenreName(event.target.value)}>
                <option value="">All genres</option>
                {filterGenres.map((genre) => <option key={genre.genreName} value={genre.genreName}>{genre.genreName}</option>)}
              </select>
            </label>
          )}

          {view !== 'genres' && (
            <button className="primary-action compact filter-action" type="submit" disabled={loading}>
              <Search size={18} />
              Search
            </button>
          )}
          {hasFilters && (
            <button className="filter-action" type="button" onClick={clearFilters} disabled={loading}>
              <RotateCcw size={17} />
              Clear
            </button>
          )}
        </form>
        <StatusMessage error={error} message={null} />
      </div>

      {view === 'songs' && (
        <div className="result-grid">
          {songs.map((song) => (
            <article className="result-card" key={song.songId}>
              <h3>{song.title}</h3>
              <p>{song.mainPerformer.nickname} · {song.genres.join(', ') || 'No genres'}</p>
              <span>{song.album?.albumName ?? 'Single'} · {song.releaseDate}</span>
              <div className="card-actions">
                <button type="button" onClick={() => playNow(song, songs.slice(songs.indexOf(song) + 1))}><Play size={15} /> Play</button>
                <button type="button" onClick={() => addToQueue(song)}><ListPlus size={15} /> Queue</button>
                <button className={isFavorite(song.songId) ? 'favorite active' : 'favorite'} type="button" aria-label={isFavorite(song.songId) ? `Remove ${song.title} from favorites` : `Add ${song.title} to favorites`} onClick={() => void toggleFavorite(song)}><Heart size={15} fill={isFavorite(song.songId) ? 'currentColor' : 'none'} /></button>
              </div>
            </article>
          ))}
          {!loading && !songs.length && <EmptyState>No songs match these filters.</EmptyState>}
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
          {!loading && !performers.length && <EmptyState>No performers match your search.</EmptyState>}
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
          {!loading && !albums.length && <EmptyState>No albums match these filters.</EmptyState>}
        </div>
      )}

      {view === 'genres' && (
        <div className="chip-grid">
          {visibleGenres.map((genre) => <span key={genre.genreName}>{genre.genreName}</span>)}
          {!loading && !visibleGenres.length && <EmptyState>No genres match your search.</EmptyState>}
        </div>
      )}
    </div>
  );
}
