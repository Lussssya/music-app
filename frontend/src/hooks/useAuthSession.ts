import { useMemo, useState } from 'react';
import type { AuthSession, AuthUser } from '../types';

const STORAGE_KEY = 'music-app-auth-session';

export function useAuthSession() {
  const [session, setSessionState] = useState<AuthSession | null>(() => readSession());

  return useMemo(() => ({
    session,
    setSession: (nextSession: AuthSession) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession));
      setSessionState(nextSession);
    },
    updateUser: (user: AuthUser) => {
      setSessionState((currentSession) => {
        if (!currentSession) {
          return null;
        }
        const nextSession = { ...currentSession, user };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession));
        return nextSession;
      });
    },
    logout: () => {
      localStorage.removeItem(STORAGE_KEY);
      setSessionState(null);
    }
  }), [session]);
}

function readSession(): AuthSession | null {
  const rawSession = localStorage.getItem(STORAGE_KEY);
  if (!rawSession) {
    return null;
  }

  try {
    return JSON.parse(rawSession) as AuthSession;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}
