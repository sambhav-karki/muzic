import assert from 'node:assert/strict'
import test from 'node:test'
import { nextIndex, planNext, uniqueSongs } from '../src/components/player/queue.ts'

test('playlist wraps locally while standalone consumes queued recommendations then requests continuation', () => {
  assert.equal(planNext(2, 3, true, false), 0)
  assert.equal(planNext(0, 3, false, true), 1)
  assert.equal(planNext(2, 3, false, false), null)
  assert.equal(planNext(0, 1, false, true), null)
})

test('playlist next wraps; shuffle selects every alternative and never the current track', () => {
  assert.equal(nextIndex(2, 3, false), 0)
  assert.equal(nextIndex(0, 1, true), 0)
  for (let current = 0; current < 4; current++) {
    const results = [0, 0.34, 0.67].map(random => nextIndex(current, 4, true, () => random))
    assert.equal(new Set(results).size, 3)
    assert.ok(results.every(index => index !== current && index >= 0 && index < 4))
  }
})

test('continuation queue removes previously heard, duplicate, and invalid video IDs', () => {
  const song = (id: string) => ({ title: id, artist: 'Artist', youtubeVideoId: id, thumbnailUrl: null })
  assert.deepEqual(uniqueSongs([
    song('aaaaaaaaaaa'), song('bbbbbbbbbbb'), song('bbbbbbbbbbb'), song('invalid'), song('ccccccccccc'),
  ], new Set(['aaaaaaaaaaa'])).map(track => track.youtubeVideoId), ['bbbbbbbbbbb', 'ccccccccccc'])
})

