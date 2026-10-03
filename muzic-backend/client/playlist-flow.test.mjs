import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPlaylistFlow } from './playlist-flow.mjs';

const songs = [{ title: 'Song', artist: 'Artist', youtubeVideoId: 'abcDEFG_123', thumbnailUrl: 'https://i.ytimg.com/image.jpg' }];
const response = (data, ok = true) => ({ ok, status: ok ? 200 : 503, json: async () => data });

test('saves curated songs then syncs and exposes loading and the playlist link', async () => {
  const calls = [], states = [];
  const flow = createPlaylistFlow({ onState: state => states.push(state), fetchImpl: async (url, options) => {
    calls.push({ url, options });
    return response(calls.length === 1 ? { id: 'local-id' } : { synced: true, youtubePlaylistId: 'remote-id' });
  }});
  const first = flow.saveAndSync('Jazz', songs);
  assert.equal(flow.saveAndSync('Jazz', songs), first);
  const result = await first;
  assert.deepEqual(calls.map(call => call.url), ['/api/playlists', '/api/playlists/local-id/sync-youtube']);
  assert.deepEqual(JSON.parse(calls[0].options.body), { name: 'Jazz', songs });
  assert.equal(calls[0].options.credentials, 'include');
  assert.deepEqual(states.map(state => state.phase), ['saving', 'syncing', 'success']);
  assert.equal(result.url, 'https://www.youtube.com/playlist?list=remote-id');
  assert.equal(result.loading, false);
  await flow.saveAndSync('Jazz', songs);
  assert.equal(calls.length, 2);
});

test('failed sync retries the saved ID without saving a second playlist', async () => {
  const calls = [], states = [];
  const flow = createPlaylistFlow({ onState: state => states.push(state), fetchImpl: async url => {
    calls.push(url);
    if (calls.length === 1) return response({ id: 'local-id' });
    if (calls.length === 2) return response({ detail: 'Quota reached' }, false);
    return response({ synced: true, youtubePlaylistId: 'remote-id' });
  }});
  await assert.rejects(flow.saveAndSync('Jazz', songs), /Quota reached/);
  assert.equal(states.at(-1).loading, false);
  assert.match(states.at(-1).message, /Playlist saved/);
  await flow.saveAndSync('Jazz', songs);
  assert.deepEqual(calls, ['/api/playlists', '/api/playlists/local-id/sync-youtube', '/api/playlists/local-id/sync-youtube']);
});

test('empty recommendations produce feedback without an API call', async () => {
  const flow = createPlaylistFlow({ fetchImpl: () => assert.fail('Unexpected request') });
  await assert.rejects(flow.saveAndSync('Jazz', []), /No verified songs/);
});
