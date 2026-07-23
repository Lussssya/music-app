import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, ListPlus, Play, RefreshCw, SkipForward, Trash2 } from 'lucide-react';
import { deleteListeningHistory, getListeningHistory } from '../api/client';
import type { AuthSession, ListeningHistoryItem, PageResponse } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';
import { usePlayer } from './PlayerProvider';

type HistoryScreenProps = {
  session: AuthSession;
};

const pageSize = 20;

export function HistoryScreen({ session }: HistoryScreenProps) {
  const { playNow, addToQueue } = usePlayer();
  const [history, setHistory] = useState<PageResponse<ListeningHistoryItem> | null>(null);
  const [page, setPage] = useState(0);
  const [skipped, setSkipped] = useState('all');
  const [loading, setLoading] = useState(true);
  const [clearing, setClearing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void loadHistory();
  }, [page, skipped, session.user.listenerId]);

  async function loadHistory() {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
      if (skipped !== 'all') params.set('skipped', skipped);
      setHistory(await getListeningHistory(session, params));
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setLoading(false);
    }
  }

  async function clearHistory() {
    if (!window.confirm('Clear all of your listening history? This cannot be undone.')) return;
    setClearing(true);
    setError(null);
    try {
      await deleteListeningHistory(session);
      setHistory(null);
      setPage(0);
      setMessage('Listening history cleared.');
    } catch (caughtError) {
      setError(displayError(caughtError));
    } finally {
      setClearing(false);
    }
  }

  const entries = history?.content ?? [];
  const songs = entries.map((entry) => entry.song);

  return (
    <div className="screen-stack">
      <div className="panel history-hero">
        <div>
          <p className="eyebrow">Your activity</p>
          <h2>Listening History</h2>
          <p>Return to tracks you recently played, or pick up a queue from where you left off.</p>
        </div>
        <div className="library-summary">
          <strong>{history?.totalElements ?? 0}</strong>
          <span>recorded plays</span>
        </div>
        <div className="history-controls">
          <label>Show
            <select value={skipped} onChange={(event) => { setPage(0); setSkipped(event.target.value); }}>
              <option value="all">All plays</option>
              <option value="false">Completed plays</option>
              <option value="true">Skipped tracks</option>
            </select>
          </label>
          <button type="button" onClick={() => void loadHistory()} disabled={loading}><RefreshCw size={17} /> Refresh</button>
          <button className="danger-action" type="button" onClick={() => void clearHistory()} disabled={clearing || !history?.totalElements}><Trash2 size={17} /> Clear history</button>
        </div>
        <StatusMessage error={error} message={message} />
      </div>

      <div className="library-list">
        {entries.map((entry, index) => (
          <article className="history-row" key={`${entry.song.songId}-${entry.playedAt}-${index}`}>
            <span className="history-time">{formatPlayedAt(entry.playedAt)}</span>
            <button className="library-track" type="button" onClick={() => playNow(entry.song, songs.slice(index + 1))}>
              <strong>{entry.song.title}</strong>
              <span>{entry.song.mainPerformer.nickname} · {entry.song.album?.albumName ?? 'Single'}</span>
            </button>
            <span className={entry.skipped ? 'history-status skipped' : 'history-status'}>{entry.skipped ? <><SkipForward size={14} /> Skipped</> : 'Played'}</span>
            <button className="icon-action" type="button" aria-label={`Play ${entry.song.title}`} onClick={() => playNow(entry.song, songs.slice(index + 1))}><Play size={18} /></button>
            <button className="icon-action" type="button" aria-label={`Add ${entry.song.title} to queue`} onClick={() => addToQueue(entry.song)}><ListPlus size={18} /></button>
          </article>
        ))}
        {loading && <EmptyState>Loading your listening history…</EmptyState>}
        {!loading && !entries.length && <EmptyState>No matching plays yet. Play a track to start building your history.</EmptyState>}
      </div>

      {history && history.totalPages > 1 && (
        <div className="history-pagination">
          <button type="button" onClick={() => setPage((current) => current - 1)} disabled={history.first}><ChevronLeft size={17} /> Previous</button>
          <span>Page {history.number + 1} of {history.totalPages}</span>
          <button type="button" onClick={() => setPage((current) => current + 1)} disabled={history.last}>Next <ChevronRight size={17} /></button>
        </div>
      )}
    </div>
  );
}

function formatPlayedAt(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
