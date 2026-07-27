import { FormEvent, useEffect, useState } from 'react';
import { ArrowRight, Disc3, Heart, ListPlus, Play, Search, Sparkles, TrendingUp, UserRound } from 'lucide-react';
import {
  getAlbums,
  getFollowedPerformerReleases,
  getGenres,
  getNewReleases,
  getPerformers,
  getSearchSuggestions,
  getSongs,
  getTrendingSongs
} from '../api/client';
import type {
  Album,
  AuthSession,
  Genre,
  PageResponse,
  Performer,
  SearchSuggestion,
  Song,
  TrendingSong
} from '../types';
import {
  AlbumDetailPage,
  DiscoveryCollectionPage,
  type DiscoveryCollection,
  PerformerDetailPage
} from './CatalogDetailPages';
import { CatalogPagination, CatalogSongList } from './CatalogUi';
import { useLibrary } from './LibraryProvider';
import { usePlayer } from './PlayerProvider';
import { StatusMessage, displayError } from './ScreenHelpers';

type BrowseView = 'songs' | 'performers' | 'albums' | 'genres';
type CatalogTarget =
  | { kind: 'home' }
  | { kind: 'performer'; performerId: number }
  | { kind: 'album'; albumId: number }
  | { kind: 'collection'; collection: DiscoveryCollection };

export function CatalogScreen({ session }: { session: AuthSession }) {
  const { playNow, addToQueue } = usePlayer();
  const { isFavorite, toggleFavorite } = useLibrary();
  const [target, setTarget] = useState<CatalogTarget>({ kind: 'home' });
  const [browseView, setBrowseView] = useState<BrowseView | null>(null);
  const [query, setQuery] = useState('');
  const [genreName, setGenreName] = useState('');
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [newReleases, setNewReleases] = useState<PageResponse<Song> | null>(null);
  const [trending, setTrending] = useState<PageResponse<TrendingSong> | null>(null);
  const [followedReleases, setFollowedReleases] = useState<PageResponse<Song> | null>(null);
  const [songResults, setSongResults] = useState<PageResponse<Song> | null>(null);
  const [performerResults, setPerformerResults] = useState<PageResponse<Performer> | null>(null);
  const [albumResults, setAlbumResults] = useState<PageResponse<Album> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadOverview();
  }, [session]);

  useEffect(() => {
    const normalized = query.trim();
    if (normalized.length < 2) {
      setSuggestions([]);
      setSuggestionsLoading(false);
      return;
    }

    let active = true;
    setSuggestionsLoading(true);
    const timer = window.setTimeout(() => {
      void getSearchSuggestions(normalized)
        .then((nextSuggestions) => {
          if (active) setSuggestions(nextSuggestions);
        })
        .catch((caught) => {
          if (active) setError(displayError(caught));
        })
        .finally(() => {
          if (active) setSuggestionsLoading(false);
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [query]);

  async function loadOverview() {
    setLoading(true);
    setError(null);
    const results = await Promise.allSettled([
      getGenres(),
      getNewReleases(0, 6),
      getTrendingSongs(0, 6, 30),
      getFollowedPerformerReleases(session, 0, 6)
    ]);

    if (results[0].status === 'fulfilled') setGenres(results[0].value);
    if (results[1].status === 'fulfilled') setNewReleases(results[1].value);
    if (results[2].status === 'fulfilled') setTrending(results[2].value);
    if (results[3].status === 'fulfilled') setFollowedReleases(results[3].value);

    const failure = results.find((result) => result.status === 'rejected');
    if (failure?.status === 'rejected') setError(displayError(failure.reason));
    setLoading(false);
  }

  async function loadBrowse(view: BrowseView, page = 0, nextQuery = query.trim(), nextGenre = genreName) {
    setLoading(true);
    setError(null);
    setSuggestions([]);
    try {
      if (view === 'songs') {
        const params = new URLSearchParams();
        if (nextQuery) params.set('search', nextQuery);
        if (nextGenre) params.set('genreName', nextGenre);
        setSongResults(await getSongs(params, page, 20));
      } else if (view === 'performers') {
        setPerformerResults(await getPerformers(nextQuery, page, 20));
      } else if (view === 'albums') {
        setAlbumResults(await getAlbums(nextQuery, '', page, 20));
      }
      setBrowseView(view);
    } catch (caught) {
      setError(displayError(caught));
    } finally {
      setLoading(false);
    }
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadBrowse(browseView && browseView !== 'genres' ? browseView : 'songs', 0);
  }

  function openTarget(nextTarget: CatalogTarget) {
    setTarget(nextTarget);
    setSuggestions([]);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function openGenre(nextGenre: string) {
    setGenreName(nextGenre);
    setQuery('');
    void loadBrowse('songs', 0, '', nextGenre);
  }

  function chooseSuggestion(suggestion: SearchSuggestion) {
    setSuggestions([]);
    if (suggestion.type === 'performer') {
      openTarget({ kind: 'performer', performerId: suggestion.entityId });
    } else if (suggestion.type === 'album') {
      openTarget({ kind: 'album', albumId: suggestion.entityId });
    } else {
      setQuery(suggestion.title);
      setGenreName('');
      void loadBrowse('songs', 0, suggestion.title, '');
    }
  }

  const backToDiscovery = () => openTarget({ kind: 'home' });
  const openPerformer = (performerId: number) => openTarget({ kind: 'performer', performerId });
  const openAlbum = (albumId: number) => openTarget({ kind: 'album', albumId });

  if (target.kind === 'performer') {
    return <PerformerDetailPage performerId={target.performerId} session={session} onBack={backToDiscovery} onPerformer={openPerformer} onAlbum={openAlbum} />;
  }
  if (target.kind === 'album') {
    return <AlbumDetailPage albumId={target.albumId} onBack={backToDiscovery} onPerformer={openPerformer} onAlbum={openAlbum} />;
  }
  if (target.kind === 'collection') {
    return <DiscoveryCollectionPage collection={target.collection} session={session} onBack={backToDiscovery} onPerformer={openPerformer} onAlbum={openAlbum} />;
  }

  const activePage = browseView === 'songs'
    ? songResults
    : browseView === 'performers'
      ? performerResults
      : browseView === 'albums'
        ? albumResults
        : null;

  return (
    <div className="screen-stack catalog-discovery">
      <section className="catalog-discovery-hero">
        <p className="eyebrow">Explore</p>
        <h2>Find your next favorite.</h2>
        <p>Search the full catalog or explore what is new, trending, and connected to performers you follow.</p>
        <form className="catalog-search" onSubmit={submitSearch}>
          <Search size={19} />
          <input
            aria-label="Search catalog"
            type="search"
            placeholder="Songs, performers, or albums"
            value={query}
            maxLength={80}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button type="submit">Search</button>
          {(suggestionsLoading || suggestions.length > 0) && (
            <div className="catalog-suggestions" role="listbox" aria-label="Search suggestions">
              {suggestionsLoading && <span>Finding matches…</span>}
              {!suggestionsLoading && suggestions.map((suggestion) => (
                <button type="button" role="option" key={`${suggestion.type}-${suggestion.entityId}`} onClick={() => chooseSuggestion(suggestion)}>
                  {suggestion.type === 'performer' ? <UserRound size={17} /> : <Disc3 size={17} />}
                  <span><strong>{suggestion.title}</strong><small>{suggestion.subtitle}</small></span>
                  <ArrowRight size={15} />
                </button>
              ))}
            </div>
          )}
        </form>
      </section>

      <nav className="catalog-navigation" aria-label="Catalog sections">
        <button className={browseView === null ? 'active' : ''} type="button" onClick={() => setBrowseView(null)}>Discover</button>
        {(['songs', 'performers', 'albums', 'genres'] as BrowseView[]).map((view) => (
          <button
            className={browseView === view ? 'active' : ''}
            type="button"
            key={view}
            onClick={() => view === 'genres' ? setBrowseView('genres') : void loadBrowse(view, 0)}
          >
            {view}
          </button>
        ))}
      </nav>

      <StatusMessage error={error} message={null} />

      {browseView === null && (
        <>
          <DiscoveryHeading title="Browse by genre" subtitle="Start broad, then narrow down." />
          <div className="catalog-genre-grid">
            {genres.map((genre, index) => (
              <button type="button" key={genre.genreName} data-tone={index % 6} onClick={() => openGenre(genre.genreName)}>
                <span>{genre.genreName}</span><ArrowRight size={18} />
              </button>
            ))}
          </div>

          <DiscoveryHeading title="New releases" subtitle="Newest catalog additions first." action={() => openTarget({ kind: 'collection', collection: 'new' })} />
          <div className="catalog-card-grid">
            {newReleases?.content.map((song, index) => (
              <DiscoverySongCard
                key={song.songId}
                song={song}
                badge={song.releaseDate}
                onPlay={() => playNow(song, newReleases.content.slice(index + 1))}
                onQueue={() => addToQueue(song)}
                onFavorite={() => void toggleFavorite(song)}
                favorite={isFavorite(song.songId)}
                onOpen={() => song.album ? openAlbum(song.album.albumId) : openPerformer(song.mainPerformer.performerId)}
              />
            ))}
          </div>

          <DiscoveryHeading title="Trending now" subtitle="Non-skipped streams from the last 30 days." icon={<TrendingUp size={18} />} action={() => openTarget({ kind: 'collection', collection: 'trending' })} />
          <div className="catalog-card-grid">
            {trending?.content.map((item, index) => (
              <DiscoverySongCard
                key={item.song.songId}
                song={item.song}
                badge={`${item.streamCount} streams · ${item.listenerCount} listeners`}
                onPlay={() => playNow(item.song, trending.content.slice(index + 1).map((entry) => entry.song))}
                onQueue={() => addToQueue(item.song)}
                onFavorite={() => void toggleFavorite(item.song)}
                favorite={isFavorite(item.song.songId)}
                onOpen={() => item.song.album ? openAlbum(item.song.album.albumId) : openPerformer(item.song.mainPerformer.performerId)}
              />
            ))}
            {!loading && trending && !trending.content.length && <p className="empty-state">Trending will appear after listeners record meaningful streams.</p>}
          </div>

          <DiscoveryHeading title="From performers you follow" subtitle="A recent-first feed based on your follows." icon={<Sparkles size={18} />} action={() => openTarget({ kind: 'collection', collection: 'following' })} />
          <div className="catalog-card-grid">
            {followedReleases?.content.map((song, index) => (
              <DiscoverySongCard
                key={song.songId}
                song={song}
                badge={song.releaseDate}
                onPlay={() => playNow(song, followedReleases.content.slice(index + 1))}
                onQueue={() => addToQueue(song)}
                onFavorite={() => void toggleFavorite(song)}
                favorite={isFavorite(song.songId)}
                onOpen={() => song.album ? openAlbum(song.album.albumId) : openPerformer(song.mainPerformer.performerId)}
              />
            ))}
            {!loading && followedReleases && !followedReleases.content.length && <p className="empty-state">Follow performers to build this feed.</p>}
          </div>
        </>
      )}

      {browseView !== null && (
        <section className="catalog-browse-results">
          <div className="catalog-section-heading">
            <div>
              <p className="eyebrow">{genreName && browseView === 'songs' ? genreName : 'Catalog'}</p>
              <h3>{browseView === 'genres' ? 'Browse genres' : `${browseView[0].toUpperCase()}${browseView.slice(1)}`}</h3>
            </div>
            {(query || genreName) && (
              <button type="button" className="text-button" onClick={() => {
                setQuery('');
                setGenreName('');
                if (browseView !== 'genres') void loadBrowse(browseView, 0, '', '');
              }}>Clear filters</button>
            )}
          </div>

          {loading && <div className="panel" aria-busy="true">Loading catalog…</div>}
          {!loading && browseView === 'songs' && <CatalogSongList songs={songResults?.content ?? []} emptyMessage="No songs match this search." onPerformer={openPerformer} onAlbum={openAlbum} />}
          {!loading && browseView === 'performers' && (
            <div className="catalog-card-grid">
              {performerResults?.content.map((performer) => (
                <button className="catalog-media-card" type="button" key={performer.performerId} onClick={() => openPerformer(performer.performerId)}>
                  {performer.pictureUrl ? <img src={performer.pictureUrl} alt="" /> : <div className="catalog-cover"><UserRound size={34} /></div>}
                  <strong>{performer.nickname}</strong>
                  <span>{performer.performerType.replace(/_/g, ' ')}{performer.verified ? ' · verified' : ''}</span>
                </button>
              ))}
              {!performerResults?.content.length && <p className="empty-state">No performers match this search.</p>}
            </div>
          )}
          {!loading && browseView === 'albums' && (
            <div className="catalog-card-grid">
              {albumResults?.content.map((album) => (
                <button className="catalog-media-card" type="button" key={album.albumId} onClick={() => openAlbum(album.albumId)}>
                  <div className="catalog-cover"><Disc3 size={34} /></div>
                  <strong>{album.albumName}</strong>
                  <span>{album.performer.nickname} · {album.releaseDate}</span>
                </button>
              ))}
              {!albumResults?.content.length && <p className="empty-state">No albums match this search.</p>}
            </div>
          )}
          {!loading && browseView === 'genres' && (
            <div className="catalog-genre-grid">
              {genres
                .filter((genre) => genre.genreName.toLowerCase().includes(query.trim().toLowerCase()))
                .map((genre, index) => <button type="button" key={genre.genreName} data-tone={index % 6} onClick={() => openGenre(genre.genreName)}><span>{genre.genreName}</span><ArrowRight size={18} /></button>)}
            </div>
          )}
          {activePage && <CatalogPagination page={activePage as PageResponse<unknown>} onChange={(page) => void loadBrowse(browseView, page)} />}
        </section>
      )}
    </div>
  );
}

function DiscoveryHeading({ title, subtitle, icon, action }: { title: string; subtitle: string; icon?: React.ReactNode; action?: () => void }) {
  return (
    <div className="catalog-section-heading">
      <div><p className="eyebrow">{icon} Discovery</p><h3>{title}</h3><span>{subtitle}</span></div>
      {action && <button className="text-button" type="button" onClick={action}>See all <ArrowRight size={16} /></button>}
    </div>
  );
}

function DiscoverySongCard({
  song,
  badge,
  favorite,
  onPlay,
  onQueue,
  onFavorite,
  onOpen
}: {
  song: Song;
  badge: string;
  favorite: boolean;
  onPlay: () => void;
  onQueue: () => void;
  onFavorite: () => void;
  onOpen: () => void;
}) {
  return (
    <article className="catalog-release-card">
      <button className="catalog-release-main" type="button" onClick={onOpen}>
        <div className="catalog-cover"><Disc3 size={36} /></div>
        <strong>{song.title}</strong>
        <span>{song.mainPerformer.nickname}</span>
        <small>{badge}</small>
      </button>
      <div className="song-list-actions">
        <button type="button" aria-label={`Play ${song.title}`} onClick={onPlay}><Play size={16} /></button>
        <button type="button" aria-label={`Queue ${song.title}`} onClick={onQueue}><ListPlus size={16} /></button>
        <button className={favorite ? 'favorite active' : 'favorite'} type="button" aria-label={`${favorite ? 'Remove' : 'Add'} ${song.title} ${favorite ? 'from' : 'to'} favorites`} onClick={onFavorite}><Heart size={16} fill={favorite ? 'currentColor' : 'none'} /></button>
      </div>
    </article>
  );
}
