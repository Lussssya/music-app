import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  AUTH_SESSION_INVALID_EVENT,
  ApiError,
  apiRequest,
  clearCsrfToken,
  getSearchSuggestions,
  getSongs,
  login
} from './client';
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
  }
};

describe('apiRequest error states', () => {
  afterEach(() => {
    clearCsrfToken();
    vi.unstubAllGlobals();
  });

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

  it('gets a CSRF token and sends it with every unsafe request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-XSRF-TOKEN',
        token: 'csrf-token'
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ saved: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiRequest<{ saved: boolean }>('/listener/me/songs/1/stream', {
      method: 'POST',
      session
    })).resolves.toEqual({ saved: true });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/csrf', expect.objectContaining({ credentials: 'include' }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/listener/me/songs/1/stream', expect.objectContaining({
      credentials: 'include',
      headers: expect.any(Headers)
    }));
    const [, request] = fetchMock.mock.calls[1];
    expect((request.headers as Headers).get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect((request.headers as Headers).get('Authorization')).toBeNull();
  });

  it('explains when the browser does not retain the session cookie', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-CSRF-TOKEN',
        token: 'csrf-token'
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(session.user), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(login({ username: 'listener', password: 'password123' })).rejects.toEqual(
      new ApiError('Sign-in could not be saved. Allow first-party cookies for this site and try again.', 401)
    );
  });
});

describe('catalog discovery requests', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps catalog results paginated and preserves filters', async () => {
    const page = {
      content: [],
      number: 2,
      size: 20,
      totalElements: 45,
      totalPages: 3,
      first: false,
      last: true
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(page), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(getSongs(new URLSearchParams({ search: 'aurora', genreName: 'Pop' }), 2, 20))
      .resolves.toEqual(page);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/songs?search=aurora&genreName=Pop&page=2&size=20',
      expect.objectContaining({ credentials: 'include' })
    );
  });

  it('normalizes the search suggestion request', async () => {
    const suggestions = [{
      type: 'performer',
      entityId: 1,
      title: 'Aurora Sky',
      subtitle: 'Solo artist'
    }];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(suggestions), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(getSearchSuggestions('  aurora  ', 8)).resolves.toEqual(suggestions);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/discovery/suggestions?query=aurora&limit=8',
      expect.objectContaining({ credentials: 'include' })
    );
  });
});
