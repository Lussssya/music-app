import { useState } from 'react';
import { RefreshCw, Sparkles } from 'lucide-react';
import { getRecommendations, rebuildRecommendations } from '../api/client';
import type { AuthSession, Recommendation } from '../types';
import { EmptyState, StatusMessage, displayError } from './ScreenHelpers';

type RecommendationsScreenProps = {
  session: AuthSession;
};

export function RecommendationsScreen({ session }: RecommendationsScreenProps) {
  const [limit, setLimit] = useState('10');
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function run(action: () => Promise<Recommendation[]>, successMessage: string) {
    setError(null);
    setMessage(null);
    try {
      setRecommendations(await action());
      setMessage(successMessage);
    } catch (caughtError) {
      setError(displayError(caughtError));
    }
  }

  return (
    <div className="screen-stack">
      <div className="panel">
        <h2>Recommendations</h2>
        <div className="toolbar-form">
          <input value={limit} onChange={(event) => setLimit(event.target.value)} placeholder="Limit" />
          <button type="button" onClick={() => run(() => getRecommendations(session, limit), 'Recommendations loaded.')}><RefreshCw size={16} /> Load</button>
          <button className="primary-action compact" type="button" onClick={() => run(() => rebuildRecommendations(session, limit), 'Recommendations rebuilt.')}><Sparkles size={16} /> Rebuild</button>
        </div>
        <StatusMessage error={error} message={message} />
      </div>

      <div className="result-grid">
        {recommendations.map((recommendation) => (
          <article className="result-card" key={recommendation.song.songId}>
            <h3>{recommendation.song.title}</h3>
            <p>{recommendation.song.mainPerformer.nickname} · {recommendation.song.genres.join(', ')}</p>
            <span>Score {Number(recommendation.score).toFixed(4)}</span>
          </article>
        ))}
        {!recommendations.length && <EmptyState>No recommendations loaded yet.</EmptyState>}
      </div>
    </div>
  );
}
