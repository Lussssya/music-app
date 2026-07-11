import { afterEach, describe, expect, it, vi } from 'vitest';
import { AUTH_SESSION_INVALID_EVENT, ApiError, apiRequest } from './client';
import type { AuthSession } from '../types';

const session: AuthSession = {
  user: {
    listenerId: 1,
    username: 'listener',
    emailAddress: 'listener@example.com',
    countryName: 'United States',
    gender: 'Female',
    dateOfBirth: '1998-04-12',
    planName: 'free',
    balance: 0
  },
  basicAuth: 'Basic dGVzdDp0ZXN0'
};

describe('apiRequest error states', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses the first structured backend error message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400,
      error: 'Bad Request',
      messages: ['Recommendation limit must be at least 1.']
    }), { status: 400, headers: { 'Content-Type': 'application/json' } })));

    await expect(apiRequest('/recommendations?limit=0', { session })).rejects.toEqual(
      new ApiError('Recommendation limit must be at least 1.', 400)
    );
  });

  it('falls back to the HTTP status when the response has no JSON body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })));

    await expect(apiRequest('/songs')).rejects.toEqual(new ApiError('Request failed with status 503', 503));
  });

  it('announces an invalid authenticated session on 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    const listener = vi.fn();
    window.addEventListener(AUTH_SESSION_INVALID_EVENT, listener);

    await expect(apiRequest('/listener/me/songs/1', { session })).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();

    window.removeEventListener(AUTH_SESSION_INVALID_EVENT, listener);
  });
});
