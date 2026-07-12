import { createContext, ReactNode, useContext, useEffect, useRef, useState } from 'react';
import { ListMusic, Pause, Play, SkipBack, SkipForward, Trash2, Volume2, X } from 'lucide-react';
import { skipSong, streamSong } from '../api/client';
import type { AuthSession, Song } from '../types';

type PlayerContextValue = {
  currentSong: Song | null;
  isPlaying: boolean;
  playNow: (song: Song, queue?: Song[]) => void;
  addToQueue: (song: Song) => void;
  togglePlayback: () => void;
};

const PlayerContext = createContext<PlayerContextValue | null>(null);
const QUEUE_STORAGE_KEY = 'music-app-play-queue';

export function usePlayer() {
  const player = useContext(PlayerContext);
  if (!player) throw new Error('usePlayer must be used within PlayerProvider.');
  return player;
}

export function PlayerProvider({ session, children }: { session: AuthSession; children: ReactNode }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const currentSongRef = useRef<Song | null>(null);
  const queueRef = useRef<Song[]>([]);
  const recordedRef = useRef(false);
  const playStartedAtRef = useRef(0);
  const [currentSong, setCurrentSong] = useState<Song | null>(null);
  const [queue, setQueue] = useState<Song[]>(() => restoreQueue());
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.8);
  const [queueOpen, setQueueOpen] = useState(false);
  const [playbackError, setPlaybackError] = useState<string | null>(null);

  useEffect(() => {
    const audio = new Audio();
    audio.preload = 'metadata';
    audio.volume = volume;
    audioRef.current = audio;

    const updateTime = () => {
      setCurrentTime(audio.currentTime);
      const meaningfulListen = audio.currentTime >= 30 || (audio.duration > 0 && audio.currentTime / audio.duration >= 0.5);
      if (meaningfulListen && currentSongRef.current && !recordedRef.current) {
        recordedRef.current = true;
        void streamSong(session, String(currentSongRef.current.songId));
      }
    };
    const updateDuration = () => setDuration(Number.isFinite(audio.duration) ? audio.duration : 0);
    const onPlay = () => setIsPlaying(true);
    const onPause = () => setIsPlaying(false);
    const onEnded = () => advance(false);
    const onError = () => {
      setPlaybackError('This track could not be loaded. Skipping to the next track.');
      setIsPlaying(false);
    };

    audio.addEventListener('timeupdate', updateTime);
    audio.addEventListener('loadedmetadata', updateDuration);
    audio.addEventListener('durationchange', updateDuration);
    audio.addEventListener('play', onPlay);
    audio.addEventListener('pause', onPause);
    audio.addEventListener('ended', onEnded);
    audio.addEventListener('error', onError);
    return () => {
      audio.pause();
      audio.removeEventListener('timeupdate', updateTime);
      audio.removeEventListener('loadedmetadata', updateDuration);
      audio.removeEventListener('durationchange', updateDuration);
      audio.removeEventListener('play', onPlay);
      audio.removeEventListener('pause', onPause);
      audio.removeEventListener('ended', onEnded);
      audio.removeEventListener('error', onError);
    };
  }, []);

  useEffect(() => {
    queueRef.current = queue;
    localStorage.setItem(QUEUE_STORAGE_KEY, JSON.stringify(queue));
  }, [queue]);

  function startSong(song: Song) {
    const audio = audioRef.current;
    if (!audio) return;
    currentSongRef.current = song;
    setCurrentSong(song);
    setPlaybackError(null);
    setCurrentTime(0);
    setDuration(0);
    recordedRef.current = false;
    playStartedAtRef.current = Date.now();
    audio.src = song.songUrl ?? '';
    if (!song.songUrl) {
      setPlaybackError('No audio is available for this track.');
      return;
    }
    void audio.play().catch(() => setPlaybackError('Playback was blocked. Press play to try again.'));
  }

  function playNow(song: Song, upcoming: Song[] = []) {
    const nextQueue = upcoming.filter((item) => item.songId !== song.songId);
    queueRef.current = nextQueue;
    setQueue(nextQueue);
    startSong(song);
  }

  function addToQueue(song: Song) {
    if (currentSongRef.current?.songId === song.songId || queueRef.current.some((item) => item.songId === song.songId)) return;
    const nextQueue = [...queueRef.current, song];
    queueRef.current = nextQueue;
    setQueue(nextQueue);
  }

  function togglePlayback() {
    const audio = audioRef.current;
    if (!audio || !currentSong?.songUrl) return;
    if (audio.paused) void audio.play().catch(() => setPlaybackError('Playback was blocked. Press play to try again.'));
    else audio.pause();
  }

  function advance(countAsSkip: boolean) {
    const playingSong = currentSongRef.current;
    if (countAsSkip && playingSong && !recordedRef.current && Date.now() - playStartedAtRef.current > 1000) {
      recordedRef.current = true;
      void skipSong(session, String(playingSong.songId));
    }
    const [next, ...rest] = queueRef.current;
    queueRef.current = rest;
    setQueue(rest);
    if (next) startSong(next);
    else {
      audioRef.current?.pause();
      currentSongRef.current = null;
      setCurrentSong(null);
      setCurrentTime(0);
      setDuration(0);
    }
  }

  function previous() {
    const audio = audioRef.current;
    if (!audio) return;
    audio.currentTime = 0;
    setCurrentTime(0);
  }

  function seek(value: number) {
    if (!audioRef.current) return;
    audioRef.current.currentTime = value;
    setCurrentTime(value);
  }

  function changeVolume(value: number) {
    setVolume(value);
    if (audioRef.current) audioRef.current.volume = value;
  }

  return (
    <PlayerContext.Provider value={{ currentSong, isPlaying, playNow, addToQueue, togglePlayback }}>
      {children}
      {(currentSong || queue.length > 0) && (
        <div className="player-shell" aria-label="Audio player">
          <div className="player-track">
            <div className="player-art"><span>{currentSong?.title.charAt(0) ?? <ListMusic size={20} />}</span></div>
            <div>
              <strong>{currentSong?.title ?? 'Queue ready'}</strong>
              <span>{currentSong?.mainPerformer.nickname ?? `${queue.length} tracks waiting`}</span>
            </div>
          </div>

          <div className="player-center">
            <div className="player-controls">
              <button type="button" aria-label="Restart track" onClick={previous} disabled={!currentSong}><SkipBack size={19} /></button>
              <button className="play-toggle" type="button" aria-label={isPlaying ? 'Pause' : 'Play'} onClick={togglePlayback} disabled={!currentSong}>
                {isPlaying ? <Pause size={20} /> : <Play size={20} />}
              </button>
              <button type="button" aria-label="Next track" onClick={() => advance(true)} disabled={!currentSong && !queue.length}><SkipForward size={19} /></button>
            </div>
            <div className="progress-row">
              <span>{formatTime(currentTime)}</span>
              <input aria-label="Track progress" type="range" min="0" max={duration || 0} step="0.1" value={Math.min(currentTime, duration || 0)} onChange={(event) => seek(Number(event.target.value))} disabled={!duration} />
              <span>{formatTime(duration)}</span>
            </div>
            {playbackError && <p className="playback-error">{playbackError}</p>}
          </div>

          <div className="player-options">
            <Volume2 size={18} />
            <input aria-label="Volume" type="range" min="0" max="1" step="0.05" value={volume} onChange={(event) => changeVolume(Number(event.target.value))} />
            <button type="button" className={queueOpen ? 'active' : ''} aria-label="Show queue" onClick={() => setQueueOpen((open) => !open)}>
              <ListMusic size={19} /><span>{queue.length}</span>
            </button>
          </div>

          {queueOpen && (
            <aside className="queue-panel" aria-label="Play queue">
              <div className="queue-heading"><h3>Up next</h3><button type="button" aria-label="Close queue" onClick={() => setQueueOpen(false)}><X size={18} /></button></div>
              {queue.map((song, index) => (
                <div className="queue-item" key={`${song.songId}-${index}`}>
                  <button type="button" onClick={() => { setQueue(queue.slice(index + 1)); startSong(song); }}>
                    <strong>{song.title}</strong><span>{song.mainPerformer.nickname}</span>
                  </button>
                  <button type="button" aria-label={`Remove ${song.title} from queue`} onClick={() => setQueue((items) => items.filter((_, itemIndex) => itemIndex !== index))}><Trash2 size={16} /></button>
                </div>
              ))}
              {!queue.length && <p>Your queue is empty.</p>}
              {queue.length > 0 && <button className="clear-queue" type="button" onClick={() => setQueue([])}>Clear queue</button>}
            </aside>
          )}
        </div>
      )}
    </PlayerContext.Provider>
  );
}

function formatTime(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${Math.floor(seconds % 60).toString().padStart(2, '0')}`;
}

function restoreQueue(): Song[] {
  try {
    return JSON.parse(localStorage.getItem(QUEUE_STORAGE_KEY) ?? '[]') as Song[];
  } catch {
    return [];
  }
}
