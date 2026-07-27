import { ArrowLeft, Check, Disc3, Play, Radio, UserPlus } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  getAlbum,
  getAlbumSongs,
  getFollowedPerformerReleases,
  getNewReleases,
  getPerformer,
  getPerformerAlbums,
  getPerformerSongs,
  getPerformerState,
  getTrendingSongs,
  setPerformerFollowing
} from '../api/client';
import type { Album, AuthSession, PageResponse, Performer, PerformerActionState, Song, TrendingSong } from '../types';
import { CatalogPagination, CatalogSongList } from './CatalogUi';
import { usePlayer } from './PlayerProvider';
import { StatusMessage, displayError } from './ScreenHelpers';

type DetailNavigation = {
  onBack: () => void;
  onPerformer: (performerId: number) => void;
  onAlbum: (albumId: number) => void;
};

export function PerformerDetailPage({
  performerId,
  session,
  onBack,
  onPerformer,
  onAlbum
}: DetailNavigation & { performerId: number; session: AuthSession }) {
  const { playQueue } = usePlayer();
  const [performer, setPerformer] = useState<Performer | null>(null);
  const [albums, setAlbums] = useState<PageResponse<Album> | null>(null);
  const [songs, setSongs] = useState<PageResponse<Song> | null>(null);
  const [state, setState] = useState<PerformerActionState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadPage();
  }, [performerId, session]);

  async function loadPage(albumPage = 0, songPage = 0) {
    setLoading(true);
    setError(null);
    try {
      const [nextPerformer, nextAlbums, nextSongs, nextState] = await Promise.all([
        getPerformer(String(performerId)),
        getPerformerAlbums(String(performerId), albumPage, 8),
        getPerformerSongs(String(performerId), songPage, 20),
        getPerformerState(session, String(performerId))
      ]);
      setPerformer(nextPerformer);
      setAlbums(nextAlbums);
      setSongs(nextSongs);
      setState(nextState);
    } catch (caught) {
      setError(displayError(caught));
    } finally {
      setLoading(false);
    }
  }

  async function changeAlbumPage(page: number) {
    try {
      setAlbums(await getPerformerAlbums(String(performerId), page, 8));
    } catch (caught) {
      setError(displayError(caught));
    }
  }

  async function changeSongPage(page: number) {
    try {
      setSongs(await getPerformerSongs(String(performerId), page, 20));
    } catch (caught) {
      setError(displayError(caught));
    }
  }

  async function toggleFollow() {
    if (!state) return;
    try {
      setState(await setPerformerFollowing(session, String(performerId), !state.following));
    } catch (caught) {
      setError(displayError(caught));
    }
  }

  if (loading) return <div className="panel" aria-busy="true">Loading performer…</div>;
  if (!performer) return <div className="panel"><button className="text-button" type="button" onClick={onBack}><ArrowLeft size={16} /> Back</button><StatusMessage error={error} message={null} /></div>;

  return (
    <div className="screen-stack catalog-detail-page">
      <button className="catalog-back" type="button" onClick={onBack}><ArrowLeft size={17} /> Back to discovery</button>
      <section className="catalog-detail-hero performer-detail-hero">
        {performer.pictureUrl
          ? <img src={performer.pictureUrl} alt="" />
          : <div className="catalog-art-fallback"><Radio size={42} /></div>}
        <div>
          <p className="eyebrow">{performer.verified ? 'Verified performer' : performer.performerType.replace(/_/g, ' ')}</p>
          <h2>{performer.nickname}</h2>
          <p>{performer.description ?? 'No performer biography yet.'}</p>
          <div className="catalog-hero-actions">
            <button className="primary-action compact" type="button" disabled={!songs?.content.length} onClick={() => songs && playQueue(songs.content)}>
              <Play size={17} /> Play
            </button>
            <button type="button" onClick={() => void toggleFollow()}>
              {state?.following ? <Check size={17} /> : <UserPlus size={17} />}
              {state?.following ? 'Following' : 'Follow'}
            </button>
          </div>
        </div>
      </section>
      <StatusMessage error={error} message={null} />

      <section className="catalog-section">
        <div className="catalog-section-heading"><div><p className="eyebrow">Discography</p><h3>Albums</h3></div></div>
        <div className="catalog-card-grid">
          {albums?.content.map((album) => (
            <button className="catalog-media-card" type="button" key={album.albumId} onClick={() => onAlbum(album.albumId)}>
              <div className="catalog-cover"><Disc3 size={34} /></div>
              <strong>{album.albumName}</strong>
              <span>{album.releaseDate}</span>
            </button>
          ))}
          {albums && !albums.content.length && <p className="empty-state">No albums yet.</p>}
        </div>
        {albums && <CatalogPagination page={albums} onChange={(page) => void changeAlbumPage(page)} />}
      </section>

      <section className="catalog-section">
        <div className="catalog-section-heading"><div><p className="eyebrow">Latest first</p><h3>Songs</h3></div></div>
        <CatalogSongList songs={songs?.content ?? []} emptyMessage="No songs yet." onPerformer={onPerformer} onAlbum={onAlbum} />
        {songs && <CatalogPagination page={songs} onChange={(page) => void changeSongPage(page)} />}
      </section>
    </div>
  );
}

export function AlbumDetailPage({
  albumId,
  onBack,
  onPerformer,
  onAlbum
}: DetailNavigation & { albumId: number }) {
  const { playQueue } = usePlayer();
  const [album, setAlbum] = useState<Album | null>(null);
  const [songs, setSongs] = useState<PageResponse<Song> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadPage();
  }, [albumId]);

  async function loadPage(page = 0) {
    setLoading(true);
    setError(null);
    try {
      const [nextAlbum, nextSongs] = await Promise.all([
        getAlbum(String(albumId)),
        getAlbumSongs(String(albumId), page, 50)
      ]);
      setAlbum(nextAlbum);
      setSongs(nextSongs);
    } catch (caught) {
      setError(displayError(caught));
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div className="panel" aria-busy="true">Loading album…</div>;
  if (!album) return <div className="panel"><button className="text-button" type="button" onClick={onBack}><ArrowLeft size={16} /> Back</button><StatusMessage error={error} message={null} /></div>;

  return (
    <div className="screen-stack catalog-detail-page">
      <button className="catalog-back" type="button" onClick={onBack}><ArrowLeft size={17} /> Back to discovery</button>
      <section className="catalog-detail-hero album-detail-hero">
        <div className="catalog-album-cover"><Disc3 size={58} /></div>
        <div>
          <p className="eyebrow">Album · {album.releaseDate}</p>
          <h2>{album.albumName}</h2>
          <button className="catalog-artist-link" type="button" onClick={() => onPerformer(album.performer.performerId)}>
            {album.performer.nickname}
          </button>
          <div className="catalog-hero-actions">
            <button className="primary-action compact" type="button" disabled={!songs?.content.length} onClick={() => songs && playQueue(songs.content)}>
              <Play size={17} /> Play album
            </button>
          </div>
        </div>
      </section>
      <StatusMessage error={error} message={null} />
      <CatalogSongList songs={songs?.content ?? []} emptyMessage="This album has no songs." onPerformer={onPerformer} onAlbum={onAlbum} />
      {songs && <CatalogPagination page={songs} onChange={(page) => void loadPage(page)} />}
    </div>
  );
}

export type DiscoveryCollection = 'new' | 'trending' | 'following';

export function DiscoveryCollectionPage({
  collection,
  session,
  onBack,
  onPerformer,
  onAlbum
}: DetailNavigation & { collection: DiscoveryCollection; session: AuthSession }) {
  const [songs, setSongs] = useState<PageResponse<Song> | null>(null);
  const [trending, setTrending] = useState<PageResponse<TrendingSong> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void load(0);
  }, [collection, session]);

  async function load(page: number) {
    setLoading(true);
    setError(null);
    try {
      if (collection === 'new') {
        setSongs(await getNewReleases(page, 20));
      } else if (collection === 'following') {
        setSongs(await getFollowedPerformerReleases(session, page, 20));
      } else {
        setTrending(await getTrendingSongs(page, 20, 30));
      }
    } catch (caught) {
      setError(displayError(caught));
    } finally {
      setLoading(false);
    }
  }

  const copy = {
    new: ['New releases', 'The newest music in the catalog.'],
    trending: ['Trending songs', 'Meaningful streams from the last 30 days.'],
    following: ['From performers you follow', 'Recent-first releases from your followed performers.']
  }[collection];
  const page = collection === 'trending' ? trending : songs;
  const content = collection === 'trending' ? trending?.content.map((item) => item.song) ?? [] : songs?.content ?? [];

  return (
    <div className="screen-stack catalog-detail-page">
      <button className="catalog-back" type="button" onClick={onBack}><ArrowLeft size={17} /> Back to discovery</button>
      <section className="panel catalog-collection-hero">
        <p className="eyebrow">Discovery</p>
        <h2>{copy[0]}</h2>
        <p>{copy[1]}</p>
      </section>
      <StatusMessage error={error} message={null} />
      {loading
        ? <div className="panel" aria-busy="true">Loading releases…</div>
        : <CatalogSongList songs={content} emptyMessage="Nothing has appeared here yet." onPerformer={onPerformer} onAlbum={onAlbum} />}
      {page && <CatalogPagination page={page as PageResponse<unknown>} onChange={(pageNumber) => void load(pageNumber)} />}
    </div>
  );
}
