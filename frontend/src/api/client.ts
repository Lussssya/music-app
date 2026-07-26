import type {
  Album,
  ApiErrorResponse,
  AuthSession,
  AuthUser,
  Genre,
  GeneratedPlaylist,
  GeneratedPlaylistSummary,
  LoginRequest,
  ListeningHistoryItem,
  PageResponse,
  Performer,
  PerformerActionState,
  Playlist,
  PlaylistRequest,
  PlaylistSummary,
  Recommendation,
  RegisterRequest,
  Song,
  SongActionState
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
export const AUTH_SESSION_INVALID_EVENT = 'music-app-auth-session-invalid';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  session?: AuthSession | null;
};

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({
    Accept: 'application/json'
  });

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (options.session?.basicAuth) {
    headers.set('Authorization', options.session.basicAuth);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (!response.ok) {
    if (response.status === 401 && options.session) {
      window.dispatchEvent(new Event(AUTH_SESSION_INVALID_EVENT));
    }
    throw new ApiError(await errorMessage(response), response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function login(request: LoginRequest): Promise<AuthUser> {
  return apiRequest<AuthUser>('/auth/login', {
    method: 'POST',
    body: request
  });
}

export function register(request: RegisterRequest): Promise<AuthUser> {
  return apiRequest<AuthUser>('/auth/register', {
    method: 'POST',
    body: request
  });
}

export function getGenres(): Promise<Genre[]> {
  return apiRequest<Genre[]>('/genres');
}

export function getFavoriteSongs(session: AuthSession): Promise<Song[]> {
  return apiRequest<Song[]>('/listener/me/library', { session });
}

export function getListeningHistory(session: AuthSession, params: URLSearchParams): Promise<PageResponse<ListeningHistoryItem>> {
  return apiRequest<PageResponse<ListeningHistoryItem>>(`/listener/me/history?${params.toString()}`, { session });
}

export function deleteListeningHistory(session: AuthSession): Promise<void> {
  return apiRequest<void>('/listener/me/history', { method: 'DELETE', session });
}

export function getSongs(params: URLSearchParams): Promise<Song[]> {
  return apiRequest<Song[]>(`/songs?${params.toString()}`);
}

export function getSong(songId: string): Promise<Song> {
  return apiRequest<Song>(`/songs/${songId}`);
}

export function getPerformers(search: string): Promise<Performer[]> {
  const params = new URLSearchParams();
  if (search.trim()) {
    params.set('search', search.trim());
  }
  return apiRequest<Performer[]>(`/performers?${params.toString()}`);
}

export function getAlbums(search: string, performerId: string): Promise<Album[]> {
  const params = new URLSearchParams();
  if (search.trim()) {
    params.set('search', search.trim());
  }
  if (performerId.trim()) {
    params.set('performerId', performerId.trim());
  }
  return apiRequest<Album[]>(`/albums?${params.toString()}`);
}

export function getSongState(session: AuthSession, songId: string): Promise<SongActionState> {
  return apiRequest<SongActionState>(`/listener/me/songs/${songId}`, { session });
}

export function streamSong(session: AuthSession, songId: string): Promise<SongActionState> {
  return apiRequest<SongActionState>(`/listener/me/songs/${songId}/stream`, {
    method: 'POST',
    session
  });
}

export function skipSong(session: AuthSession, songId: string): Promise<SongActionState> {
  return apiRequest<SongActionState>(`/listener/me/songs/${songId}/skip`, {
    method: 'POST',
    session
  });
}

export function setSongAttitude(session: AuthSession, songId: string, attitude: SongActionState['attitude']): Promise<SongActionState> {
  return apiRequest<SongActionState>(`/listener/me/songs/${songId}/attitude`, {
    method: 'PUT',
    body: { attitude },
    session
  });
}

export function clearSongAttitude(session: AuthSession, songId: string): Promise<SongActionState> {
  return apiRequest<SongActionState>(`/listener/me/songs/${songId}/attitude`, {
    method: 'DELETE',
    session
  });
}

export function setSongBlocked(session: AuthSession, songId: string, blocked: boolean): Promise<SongActionState> {
  return apiRequest<SongActionState>(`/listener/me/songs/${songId}/block`, {
    method: blocked ? 'PUT' : 'DELETE',
    session
  });
}

export function getPerformerState(session: AuthSession, performerId: string): Promise<PerformerActionState> {
  return apiRequest<PerformerActionState>(`/listener/me/performers/${performerId}`, { session });
}

export function setPerformerFollowing(session: AuthSession, performerId: string, following: boolean): Promise<PerformerActionState> {
  return apiRequest<PerformerActionState>(`/listener/me/performers/${performerId}/follow`, {
    method: following ? 'PUT' : 'DELETE',
    session
  });
}

export function setPerformerAttitude(session: AuthSession, performerId: string, attitude: PerformerActionState['attitude']): Promise<PerformerActionState> {
  return apiRequest<PerformerActionState>(`/listener/me/performers/${performerId}/attitude`, {
    method: 'PUT',
    body: { attitude },
    session
  });
}

export function clearPerformerAttitude(session: AuthSession, performerId: string): Promise<PerformerActionState> {
  return apiRequest<PerformerActionState>(`/listener/me/performers/${performerId}/attitude`, {
    method: 'DELETE',
    session
  });
}

export function setPerformerBlocked(session: AuthSession, performerId: string, blocked: boolean): Promise<PerformerActionState> {
  return apiRequest<PerformerActionState>(`/listener/me/performers/${performerId}/block`, {
    method: blocked ? 'PUT' : 'DELETE',
    session
  });
}

export function getPlaylists(params: URLSearchParams): Promise<PlaylistSummary[]> {
  return apiRequest<PlaylistSummary[]>(`/playlists?${params.toString()}`);
}

export function getPlaylist(playlistId: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}`);
}

export function getGeneratedPlaylists(session: AuthSession): Promise<GeneratedPlaylistSummary[]> {
  return apiRequest<GeneratedPlaylistSummary[]>('/playlists/generated', { session });
}

export function generatePlaylist(session: AuthSession, type: string): Promise<GeneratedPlaylist> {
  return apiRequest<GeneratedPlaylist>(`/playlists/generated/${type}`, { session });
}

export function createPlaylist(session: AuthSession, request: PlaylistRequest): Promise<Playlist> {
  return apiRequest<Playlist>('/playlists', {
    method: 'POST',
    body: request,
    session
  });
}

export function updatePlaylist(session: AuthSession, playlistId: string, request: PlaylistRequest): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}`, { method: 'PUT', body: request, session });
}

export function deletePlaylist(session: AuthSession, playlistId: string): Promise<void> {
  return apiRequest<void>(`/playlists/${playlistId}`, { method: 'DELETE', session });
}

export function joinPlaylist(session: AuthSession, playlistId: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/members/me`, {
    method: 'PUT',
    session
  });
}

export function leavePlaylist(session: AuthSession, playlistId: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/members/me`, {
    method: 'DELETE',
    session
  });
}

export function addPlaylistMember(session: AuthSession, playlistId: string, username: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/members/${encodeURIComponent(username)}`, { method: 'PUT', session });
}

export function removePlaylistMember(session: AuthSession, playlistId: string, username: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/members/${encodeURIComponent(username)}`, { method: 'DELETE', session });
}

export function addPlaylistSong(session: AuthSession, playlistId: string, songId: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/songs/${songId}`, {
    method: 'PUT',
    session
  });
}

export function removePlaylistSong(session: AuthSession, playlistId: string, songId: string): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/songs/${songId}`, {
    method: 'DELETE',
    session
  });
}

export function setPlaylistSongVote(session: AuthSession, playlistId: string, songId: string, voted: boolean): Promise<Playlist> {
  return apiRequest<Playlist>(`/playlists/${playlistId}/songs/${songId}/vote`, {
    method: voted ? 'PUT' : 'DELETE',
    session
  });
}

export function getRecommendations(session: AuthSession, page: number, size = 20): Promise<PageResponse<Recommendation>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiRequest<PageResponse<Recommendation>>(`/recommendations?${params.toString()}`, { session });
}

export function rebuildRecommendations(session: AuthSession, page: number, size = 20): Promise<PageResponse<Recommendation>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiRequest<PageResponse<Recommendation>>(`/recommendations/rebuild?${params.toString()}`, {
    method: 'POST',
    session
  });
}

export function createBasicAuth(username: string, password: string): string {
  return `Basic ${window.btoa(`${username}:${password}`)}`;
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const error = (await response.json()) as ApiErrorResponse;
    return error.messages?.[0] ?? error.error ?? `Request failed with status ${response.status}`;
  } catch {
    return `Request failed with status ${response.status}`;
  }
}
