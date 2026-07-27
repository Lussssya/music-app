import { Heart, ListPlus, Play } from 'lucide-react';
import type { PageResponse, Song } from '../types';
import { useLibrary } from './LibraryProvider';
import { usePlayer } from './PlayerProvider';

type CatalogSongListProps = {
  songs: Song[];
  emptyMessage: string;
  onPerformer?: (performerId: number) => void;
  onAlbum?: (albumId: number) => void;
};

export function CatalogSongList({ songs, emptyMessage, onPerformer, onAlbum }: CatalogSongListProps) {
  const { playNow, addToQueue } = usePlayer();
  const { isFavorite, toggleFavorite } = useLibrary();

  if (!songs.length) {
    return <p className="empty-state">{emptyMessage}</p>;
  }

  return (
    <div className="catalog-song-list">
      {songs.map((song, index) => (
        <article className="catalog-song-row" key={song.songId}>
          <span className="track-number">{index + 1}</span>
          <div className="catalog-track-copy">
            <strong>{song.title}</strong>
            <div>
              <button type="button" onClick={() => onPerformer?.(song.mainPerformer.performerId)}>
                {song.mainPerformer.nickname}
              </button>
              {song.album && (
                <>
                  <span> · </span>
                  <button type="button" onClick={() => onAlbum?.(song.album!.albumId)}>
                    {song.album.albumName}
                  </button>
                </>
              )}
            </div>
          </div>
          <span className="catalog-song-genres">{song.genres.join(', ') || 'No genre'}</span>
          <div className="song-list-actions">
            <button type="button" aria-label={`Play ${song.title}`} onClick={() => playNow(song, songs.slice(index + 1))}><Play size={16} /></button>
            <button type="button" aria-label={`Queue ${song.title}`} onClick={() => addToQueue(song)}><ListPlus size={16} /></button>
            <button
              className={isFavorite(song.songId) ? 'favorite active' : 'favorite'}
              type="button"
              aria-label={isFavorite(song.songId) ? `Remove ${song.title} from favorites` : `Add ${song.title} to favorites`}
              onClick={() => void toggleFavorite(song)}
            >
              <Heart size={16} fill={isFavorite(song.songId) ? 'currentColor' : 'none'} />
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

export function CatalogPagination<T>({ page, onChange }: { page: PageResponse<T>; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) {
    return null;
  }

  return (
    <div className="history-pagination">
      <button type="button" disabled={page.first} onClick={() => onChange(page.number - 1)}>Previous</button>
      <span>Page {page.number + 1} of {page.totalPages} · {page.totalElements} results</span>
      <button type="button" disabled={page.last} onClick={() => onChange(page.number + 1)}>Next</button>
    </div>
  );
}
