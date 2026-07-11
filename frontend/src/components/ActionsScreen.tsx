import { useState } from 'react';
import { Ban, Heart, Play, SkipForward, UserCheck, UserMinus, X } from 'lucide-react';
import {
  clearPerformerAttitude,
  clearSongAttitude,
  getPerformerState,
  getSongState,
  setPerformerAttitude,
  setPerformerBlocked,
  setPerformerFollowing,
  setSongAttitude,
  setSongBlocked,
  skipSong,
  streamSong
} from '../api/client';
import type { AuthSession, PerformerActionState, SongActionState } from '../types';
import { StatusMessage, displayError } from './ScreenHelpers';

type ActionsScreenProps = {
  session: AuthSession;
};

export function ActionsScreen({ session }: ActionsScreenProps) {
  const [songId, setSongId] = useState('1');
  const [performerId, setPerformerId] = useState('1');
  const [songState, setSongState] = useState<SongActionState | null>(null);
  const [performerState, setPerformerState] = useState<PerformerActionState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function run<T>(action: () => Promise<T>, onSuccess: (response: T) => void, successMessage: string) {
    setError(null);
    setMessage(null);
    try {
      onSuccess(await action());
      setMessage(successMessage);
    } catch (caughtError) {
      setError(displayError(caughtError));
    }
  }

  return (
    <div className="screen-stack">
      <StatusMessage error={error} message={message} />

      <div className="panel action-panel">
        <h2>Song Actions</h2>
        <div className="toolbar-form">
          <input value={songId} onChange={(event) => setSongId(event.target.value)} placeholder="Song ID" />
          <button type="button" onClick={() => run(() => getSongState(session, songId), setSongState, 'Song state loaded.')}>Load state</button>
          <button type="button" onClick={() => run(() => streamSong(session, songId), setSongState, 'Stream recorded.')}><Play size={16} /> Stream</button>
          <button type="button" onClick={() => run(() => skipSong(session, songId), setSongState, 'Skip recorded.')}><SkipForward size={16} /> Skip</button>
          <button type="button" onClick={() => run(() => setSongAttitude(session, songId, 'like'), setSongState, 'Song liked.')}><Heart size={16} /> Like</button>
          <button type="button" onClick={() => run(() => setSongAttitude(session, songId, 'dislike'), setSongState, 'Song disliked.')}>Dislike</button>
          <button type="button" onClick={() => run(() => setSongAttitude(session, songId, 'not_interested'), setSongState, 'Marked not interested.')}>Not interested</button>
          <button type="button" onClick={() => run(() => clearSongAttitude(session, songId), setSongState, 'Song attitude cleared.')}><X size={16} /> Clear</button>
          <button type="button" onClick={() => run(() => setSongBlocked(session, songId, true), setSongState, 'Song blocked.')}><Ban size={16} /> Block</button>
          <button type="button" onClick={() => run(() => setSongBlocked(session, songId, false), setSongState, 'Song unblocked.')}>Unblock</button>
        </div>
        {songState && <pre className="state-box">{JSON.stringify(songState, null, 2)}</pre>}
      </div>

      <div className="panel action-panel">
        <h2>Performer Actions</h2>
        <div className="toolbar-form">
          <input value={performerId} onChange={(event) => setPerformerId(event.target.value)} placeholder="Performer ID" />
          <button type="button" onClick={() => run(() => getPerformerState(session, performerId), setPerformerState, 'Performer state loaded.')}>Load state</button>
          <button type="button" onClick={() => run(() => setPerformerFollowing(session, performerId, true), setPerformerState, 'Performer followed.')}><UserCheck size={16} /> Follow</button>
          <button type="button" onClick={() => run(() => setPerformerFollowing(session, performerId, false), setPerformerState, 'Performer unfollowed.')}><UserMinus size={16} /> Unfollow</button>
          <button type="button" onClick={() => run(() => setPerformerAttitude(session, performerId, 'like'), setPerformerState, 'Performer liked.')}><Heart size={16} /> Like</button>
          <button type="button" onClick={() => run(() => setPerformerAttitude(session, performerId, 'dislike'), setPerformerState, 'Performer disliked.')}>Dislike</button>
          <button type="button" onClick={() => run(() => setPerformerAttitude(session, performerId, 'not_interested'), setPerformerState, 'Marked not interested.')}>Not interested</button>
          <button type="button" onClick={() => run(() => clearPerformerAttitude(session, performerId), setPerformerState, 'Performer attitude cleared.')}><X size={16} /> Clear</button>
          <button type="button" onClick={() => run(() => setPerformerBlocked(session, performerId, true), setPerformerState, 'Performer blocked.')}><Ban size={16} /> Block</button>
          <button type="button" onClick={() => run(() => setPerformerBlocked(session, performerId, false), setPerformerState, 'Performer unblocked.')}>Unblock</button>
        </div>
        {performerState && <pre className="state-box">{JSON.stringify(performerState, null, 2)}</pre>}
      </div>
    </div>
  );
}
