import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_SESSION_INVALID_EVENT, getCurrentUser, logout } from '../api/client';
import type { AuthSession } from '../types';
import { useAuthSession } from './useAuthSession';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getCurrentUser: vi.fn(),
    logout: vi.fn()
  };
});

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
  }
};

describe('useAuthSession', () => {
  beforeEach(() => {
    vi.mocked(getCurrentUser).mockReset();
    vi.mocked(logout).mockReset();
    vi.mocked(logout).mockResolvedValue(undefined);
  });

  afterEach(() => vi.clearAllMocks());

  it('restores the signed-in listener from the server session', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(session.user);
    const { result } = renderHook(() => useAuthSession());

    await waitFor(() => expect(result.current.restoring).toBe(false));
    expect(result.current.session).toEqual(session);
    expect(getCurrentUser).toHaveBeenCalledOnce();
  });

  it('keeps only non-sensitive user data in React state after login', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('Not signed in'));
    const { result } = renderHook(() => useAuthSession());
    await waitFor(() => expect(result.current.restoring).toBe(false));

    act(() => result.current.setSession(session.user));

    expect(result.current.session).toEqual(session);
    expect(localStorage.getItem('music-app-auth-session')).toBeNull();
  });

  it('logs out locally and ends the server session', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('Not signed in'));
    const { result } = renderHook(() => useAuthSession());
    await waitFor(() => expect(result.current.restoring).toBe(false));
    act(() => result.current.setSession(session.user));
    act(() => result.current.logout());

    expect(result.current.session).toBeNull();
    expect(logout).toHaveBeenCalledWith(session);
  });

  it('clears the visible session after an invalid-session event', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('Not signed in'));
    const { result } = renderHook(() => useAuthSession());
    await waitFor(() => expect(result.current.restoring).toBe(false));
    act(() => result.current.setSession(session.user));
    act(() => window.dispatchEvent(new Event(AUTH_SESSION_INVALID_EVENT)));

    expect(result.current.session).toBeNull();
  });
});
