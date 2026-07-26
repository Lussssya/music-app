import { expect, test } from '@playwright/test';

const user = {
  listenerId: 101,
  username: 'browser_listener',
  emailAddress: 'browser.listener@example.com',
  countryName: 'United States',
  gender: 'Female',
  dateOfBirth: '1998-04-12',
  planName: 'free',
  balance: 0
};

const song = {
  songId: 1,
  title: 'Northern Lights',
  songUrl: null,
  releaseDate: '2023-05-15',
  credits: 'Written by Aurora Sky',
  moneyPerStream: 0.0032,
  mainPerformer: { performerId: 1, nickname: 'Aurora Sky' },
  album: { albumId: 1, albumName: 'Northern Lights', releaseDate: '2023-05-15' },
  genres: ['Pop']
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.clear());
  let authenticated = false;

  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === '/api/auth/csrf') {
      await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'browser-csrf-token' } });
      return;
    }
    if (path === '/api/auth/me') {
      await route.fulfill(authenticated ? { json: user } : { status: 401, body: '' });
      return;
    }
    if (path === '/api/auth/register' || path === '/api/auth/login') {
      authenticated = true;
      await route.fulfill({ status: path.endsWith('register') ? 201 : 200, json: user });
      return;
    }
    if (path === '/api/auth/logout') {
      authenticated = false;
      await route.fulfill({ status: 204, body: '' });
      return;
    }
    if (path === '/api/songs') {
      await route.fulfill({ json: [song] });
      return;
    }
    if (path === '/api/listener/me/songs/1/stream') {
      await route.fulfill({ json: { listenerId: 101, songId: 1, streamCount: 1, skipCount: 0, attitude: null, blocked: false, blockedAt: null } });
      return;
    }
    if (path === '/api/playlists' && request.method() === 'POST') {
      await route.fulfill({ status: 201, json: {
        playlistId: 20,
        name: 'Browser Journey',
        type: 'private',
        playlistUrl: null,
        pictureUrl: null,
        creatorId: 101,
        creatorUsername: user.username,
        createdAt: '2026-07-12T00:00:00Z',
        memberCount: 1,
        songCount: 0,
        members: [{ listenerId: 101, username: user.username, joinedAt: '2026-07-12T00:00:00Z' }],
        songs: []
      } });
      return;
    }
    if (path === '/api/recommendations') {
      await route.fulfill({ json: pageResponse([]) });
      return;
    }
    if (path === '/api/recommendations/rebuild') {
      await route.fulfill({ json: pageResponse([{ score: 42.5, generatedAt: '2026-07-12T00:00:00Z', song }]) });
      return;
    }

    await route.fulfill({ json: [] });
  });
});

test('registration to recommendations journey works in a browser', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Register' }).click();
  await page.getByLabel('Username').fill(user.username);
  await page.getByLabel('Email').fill(user.emailAddress);
  await page.getByLabel('Gender').fill(user.gender);
  await page.getByLabel('Date of birth').fill(user.dateOfBirth);
  await page.getByLabel('Country').fill(user.countryName);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('heading', { name: user.username })).toBeVisible();
  await expect(page.getByRole('heading', { name: song.title })).toBeVisible();

  await page.getByRole('button', { name: 'Actions' }).click();
  await page.getByRole('button', { name: 'Stream' }).click();
  await expect(page.getByText('Stream recorded.')).toBeVisible();

  await page.getByRole('button', { name: 'Playlists' }).click();
  await page.getByPlaceholder('Name').fill('Browser Journey');
  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page.getByText('Playlist created.')).toBeVisible();

  await page.getByRole('button', { name: 'Recommendations' }).click();
  await page.getByRole('button', { name: 'Rebuild 100' }).click();
  await expect(page.getByText('100 recommendations rebuilt.')).toBeVisible();
  await expect(page.getByText('Score 42.5000')).toBeVisible();
});

test('a stale authenticated session returns to login', async ({ page }) => {
  await page.route('**/api/auth/me', async (route) => route.fulfill({ json: user }));
  await page.route('**/api/listener/me/songs/1', async (route) => route.fulfill({ status: 401, body: '' }));
  await page.goto('/');
  await page.getByRole('button', { name: 'Actions' }).click();
  await page.getByRole('button', { name: 'Load state' }).first().click();

  await expect(page.getByRole('heading', { name: 'Back-office shell for testing the music platform.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Login' }).last()).toBeVisible();
});

function pageResponse<T>(content: T[]) {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true
  };
}
