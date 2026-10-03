import assert from 'node:assert/strict'
import test from 'node:test'
import { readSwiped, storageKey, swipeDirection } from '../src/components/discovery/storage.ts'
const song = { title: 'Track', artist: 'Artist', youtubeVideoId: 'abcdefghijk', thumbnailUrl: null }
test('Swiped playlists isolate stable profile IDs and reject corrupt or duplicate songs', () => {
  const values = new Map([[storageKey('alice'), JSON.stringify([song, song, { ...song, youtubeVideoId: 'bad' }])]])
  const storage = { getItem: (key: string) => values.get(key) ?? null }
  assert.deepEqual(readSwiped(storage, 'alice'), [song])
  assert.deepEqual(readSwiped(storage, 'bob'), [])
  assert.notEqual(storageKey('a:b'), storageKey('a%3Ab'))
  assert.deepEqual(readSwiped({ getItem: () => '{bad' }, 'alice'), [])
})
test('swipe requires deliberate horizontal motion and leaves vertical scrolling alone', () => {
  assert.equal(swipeDirection(70, 10), 'like')
  assert.equal(swipeDirection(-70, 10), 'pass')
  assert.equal(swipeDirection(64, 0), null)
  assert.equal(swipeDirection(70, 90), null)
})
