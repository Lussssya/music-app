import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AUTH_SESSION_INVALID_EVENT } from '../api/client';
import type { AuthSession } from '../types';
import { useAuthSession } from './useAuthSession';

const session: AuthSession = {
  user: {
    listenerId: 7,
    username: 'session-user',
    emailAddress: 'session@example.com',
    countryName: 'Canada',
    gender: 'Female',
    dateOfBirth: '1997-02-03',
    planName: 'premium',
    balance: 12.5
  },
  basicAuth: 'Basic c2Vzc2lvbg=='
};

describe('useAuthSession', () => {
  it('persists and restores a login session', () => {
    const first = renderHook(() => useAuthSession());
    act(() => first.result.current.setSession(session));
    expect(JSON.parse(localStorage.getItem('music-app-auth-session')!)).toEqual(session);
    first.unmount();

    const restored = renderHook(() => useAuthSession());
    expect(restored.result.current.session).toEqual(session);
  });

  it('logs out and removes the stored session', () => {
    const { result } = renderHook(() => useAuthSession());
    act(() => result.current.setSession(session));
    act(() => result.current.logout());

    expect(result.current.session).toBeNull();
    expect(localStorage.getItem('music-app-auth-session')).toBeNull();
  });

  it('clears stale credentials after an invalid-session event', () => {
    const { result } = renderHook(() => useAuthSession());
    act(() => result.current.setSession(session));
    act(() => window.dispatchEvent(new Event(AUTH_SESSION_INVALID_EVENT)));

    expect(result.current.session).toBeNull();
    expect(localStorage.getItem('music-app-auth-session')).toBeNull();
  });

  it('discards corrupt stored session data', () => {
    localStorage.setItem('music-app-auth-session', '{not-json');
    const { result } = renderHook(() => useAuthSession());

    expect(result.current.session).toBeNull();
    expect(localStorage.getItem('music-app-auth-session')).toBeNull();
  });
});
