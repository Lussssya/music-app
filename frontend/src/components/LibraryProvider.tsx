import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { getFavoriteSongs, setSongAttitude, clearSongAttitude } from '../api/client';
import type { AuthSession, Song } from '../types';
import { displayError } from './ScreenHelpers';

type LibraryContextValue = {
  favorites: Song[];
  loading: boolean;
  error: string | null;
  isFavorite: (songId: number) => boolean;
  toggleFavorite: (song: Song) => Promise<void>;
  refreshLibrary: () => Promise<void>;
};

const LibraryContext = createContext<LibraryContextValue | null>(null);

export function useLibrary() {
  const library = useContext(LibraryContext);
  if (!library) throw new Error('useLibrary must be used within LibraryProvider.');
  return library;
}

export function LibraryProvider({ session, children }: { session: AuthSession; children: ReactNode }) {
  const [favorites, setFavorites] = useState<Song[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void refreshLibrary();
  }, [session.user.listenerId]);

  async function refreshLibrary() {
    setLoading(true);
    setError(null);
    try {
      setFavorites(await getFavoriteSongs(session));
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setLoading(false);
    }
  }

  async function toggleFavorite(song: Song) {
    const alreadyFavorite = favorites.some((favorite) => favorite.songId === song.songId);
    setError(null);
    setFavorites((items) => alreadyFavorite
      ? items.filter((favorite) => favorite.songId !== song.songId)
      : [...items, song].sort((left, right) => left.title.localeCompare(right.title)));

    try {
      if (alreadyFavorite) await clearSongAttitude(session, String(song.songId));
      else await setSongAttitude(session, String(song.songId), 'like');
    } catch (caughtError) {
      setFavorites((items) => alreadyFavorite
        ? [...items, song].sort((left, right) => left.title.localeCompare(right.title))
        : items.filter((favorite) => favorite.songId !== song.songId));
      setError(displayError(caughtError));
    }
  }

  return (
    <LibraryContext.Provider value={{
      favorites,
      loading,
      error,
      isFavorite: (songId) => favorites.some((song) => song.songId === songId),
      toggleFavorite,
      refreshLibrary
    }}>
      {children}
    </LibraryContext.Provider>
  );
}
