export type AuthUser = {
  listenerId: number;
  username: string;
  emailAddress: string;
  countryName: string;
  gender: string;
  dateOfBirth: string;
  planName: string;
  balance: number;
};

export type LoginRequest = {
  username: string;
  password: string;
};

export type RegisterRequest = LoginRequest & {
  emailAddress: string;
  gender: string;
  dateOfBirth: string;
  countryName: string;
};

export type AuthSession = {
  user: AuthUser;
  basicAuth: string;
};

export type ApiErrorResponse = {
  status?: number;
  error?: string;
  messages?: string[];
};

export type PerformerSummary = {
  performerId: number;
  nickname: string;
};

export type AlbumSummary = {
  albumId: number;
  albumName: string;
  releaseDate: string;
};

export type Song = {
  songId: number;
  title: string;
  songUrl: string | null;
  releaseDate: string;
  credits: string | null;
  moneyPerStream: number;
  mainPerformer: PerformerSummary;
  album: AlbumSummary | null;
  genres: string[];
};

export type Performer = PerformerSummary & {
  description: string | null;
  performerType: string;
  verified: boolean;
  pictureUrl: string | null;
};

export type Album = AlbumSummary & {
  albumUrl: string | null;
  performer: PerformerSummary;
  songs: Song[];
};

export type Genre = {
  genreName: string;
};

export type SongActionState = {
  listenerId: number;
  songId: number;
  streamCount: number;
  skipCount: number;
  attitude: 'like' | 'dislike' | 'not_interested' | null;
  blocked: boolean;
  blockedAt: string | null;
};

export type PerformerActionState = {
  listenerId: number;
  performerId: number;
  following: boolean;
  attitude: 'like' | 'dislike' | 'not_interested' | null;
  blocked: boolean;
  followedAt: string | null;
  blockedAt: string | null;
};

export type PlaylistSummary = {
  playlistId: number;
  name: string;
  type: string;
  playlistUrl: string | null;
  pictureUrl: string | null;
  creatorId: number;
  creatorUsername: string;
  createdAt: string;
  memberCount: number;
  songCount: number;
};

export type PlaylistSong = {
  songId: number;
  title: string;
  mainPerformerId: number;
  mainPerformerName: string;
  addedByListenerId: number | null;
  addedAt: string;
  voteCount: number;
};

export type PlaylistMember = {
  listenerId: number;
  username: string;
  joinedAt: string;
};

export type Playlist = PlaylistSummary & {
  members: PlaylistMember[];
  songs: PlaylistSong[];
};

export type PlaylistRequest = {
  name: string;
  type: string;
  playlistUrl: string | null;
  pictureUrl: string | null;
};

export type GeneratedPlaylistType =
  | 'DAILY_REWIND'
  | 'WEEKLY_REWIND'
  | 'ALL_TIME_REWIND'
  | 'FORGOTTEN_GEMS'
  | 'COMFORT_SONGS'
  | 'NO_SKIPS'
  | 'HIDDEN_FAVOURITES'
  | 'GENRE_MIX'
  | 'REDISCOVER';

export type GeneratedPlaylistSummary = {
  type: GeneratedPlaylistType;
  name: string;
  description: string;
};

export type GeneratedPlaylist = GeneratedPlaylistSummary & {
  songs: Song[];
};

export type Recommendation = {
  score: number;
  generatedAt: string;
  song: Song;
};

export type PageResponse<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type ListeningHistoryItem = {
  song: Song;
  playedAt: string;
  skipped: boolean;
};
