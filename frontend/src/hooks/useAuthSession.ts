import { useEffect, useMemo, useState } from 'react';
import { AUTH_SESSION_INVALID_EVENT, getCurrentUser, logout as endServerSession } from '../api/client';
import type { AuthSession, AuthUser } from '../types';

export function useAuthSession() {
  const [session, setSessionState] = useState<AuthSession | null>(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    const clearInvalidSession = () => {
      setSessionState(null);
    };

    window.addEventListener(AUTH_SESSION_INVALID_EVENT, clearInvalidSession);
    return () => window.removeEventListener(AUTH_SESSION_INVALID_EVENT, clearInvalidSession);
  }, []);

  useEffect(() => {
    let active = true;

    void getCurrentUser()
      .then((user) => {
        if (active) {
          setSessionState({ user });
        }
      })
      .catch(() => {
        if (active) {
          setSessionState(null);
        }
      })
      .finally(() => {
        if (active) {
          setRestoring(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return useMemo(() => ({
    session,
    restoring,
    setSession: (user: AuthUser) => {
      setSessionState({ user });
    },
    updateUser: (user: AuthUser) => {
      setSessionState((currentSession) => {
        if (!currentSession) {
          return null;
        }
        return { ...currentSession, user };
      });
    },
    logout: () => {
      const currentSession = session;
      setSessionState(null);
      if (currentSession) {
        void endServerSession(currentSession).catch(() => undefined);
      }
    }
  }), [restoring, session]);
}
