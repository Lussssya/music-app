import { useState } from 'react';
import { Disc3, Heart, HeartHandshake, History, Library, ListMusic, LogOut, Sparkles, UserCircle } from 'lucide-react';
import { ActionsScreen } from './components/ActionsScreen';
import { AuthPanel } from './components/AuthPanel';
import { CatalogScreen } from './components/CatalogScreen';
import { PlaylistsScreen } from './components/PlaylistsScreen';
import { RecommendationsScreen } from './components/RecommendationsScreen';
import { PlayerProvider } from './components/PlayerProvider';
import { LibraryProvider } from './components/LibraryProvider';
import { LibraryScreen } from './components/LibraryScreen';
import { HistoryScreen } from './components/HistoryScreen';
import { useAuthSession } from './hooks/useAuthSession';

type TabId = 'catalog' | 'library' | 'history' | 'playlists' | 'actions' | 'recommendations' | 'account';

const tabs: Array<{ id: TabId; label: string; icon: typeof Library }> = [
  { id: 'catalog', label: 'Catalog', icon: Library },
  { id: 'library', label: 'Library', icon: Heart },
  { id: 'history', label: 'History', icon: History },
  { id: 'playlists', label: 'Playlists', icon: ListMusic },
  { id: 'actions', label: 'Actions', icon: HeartHandshake },
  { id: 'recommendations', label: 'Recommendations', icon: Sparkles },
  { id: 'account', label: 'Account', icon: UserCircle }
];

export function App() {
  const { session, setSession, logout } = useAuthSession();
  const [activeTab, setActiveTab] = useState<TabId>('catalog');

  if (!session) {
    return <AuthPanel onAuthenticated={setSession} />;
  }

  return (
    <LibraryProvider session={session}>
    <PlayerProvider session={session}>
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Disc3 size={28} />
          <div>
            <strong>Music App</strong>
            <span>API tester</span>
          </div>
        </div>

        <nav className="tabs" aria-label="Main navigation">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                type="button"
                className={activeTab === tab.id ? 'active' : ''}
                onClick={() => setActiveTab(tab.id)}
              >
                <Icon size={18} />
                {tab.label}
              </button>
            );
          })}
        </nav>

        <button className="logout-button" type="button" onClick={logout}>
          <LogOut size={18} />
          Logout
        </button>
      </aside>

      <main className="content">
        <header className="topbar">
          <div>
            <p className="eyebrow">Signed in</p>
            <h1>{session.user.username}</h1>
          </div>
          <div className="account-pill">
            <span>{session.user.planName}</span>
            <strong>${Number(session.user.balance).toFixed(2)}</strong>
          </div>
        </header>

        <section className="workspace">
          {activeTab === 'catalog' && <CatalogScreen />}
          {activeTab === 'library' && <LibraryScreen />}
          {activeTab === 'history' && <HistoryScreen session={session} />}
          {activeTab === 'playlists' && <PlaylistsScreen session={session} />}
          {activeTab === 'actions' && <ActionsScreen session={session} />}
          {activeTab === 'recommendations' && <RecommendationsScreen session={session} />}

          {activeTab === 'account' && (
            <div className="panel">
              <h2>Account</h2>
              <dl className="account-grid">
                <div>
                  <dt>Listener ID</dt>
                  <dd>{session.user.listenerId}</dd>
                </div>
                <div>
                  <dt>Email</dt>
                  <dd>{session.user.emailAddress}</dd>
                </div>
                <div>
                  <dt>Country</dt>
                  <dd>{session.user.countryName}</dd>
                </div>
                <div>
                  <dt>Date of birth</dt>
                  <dd>{session.user.dateOfBirth}</dd>
                </div>
              </dl>
            </div>
          )}
        </section>
      </main>
    </div>
    </PlayerProvider>
    </LibraryProvider>
  );
}
