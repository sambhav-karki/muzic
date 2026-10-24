import { test } from 'node:test'
import assert from 'node:assert/strict'
import { pinKey, pinnedGrid, readPins } from '../src/components/discovery/pins.ts'
import { titleKey, artistKey } from '../src/components/discovery/identity.ts'

const songs = Array.from({ length: 12 }, (_, i) => ({ title: `Song ${i}`, artist: `Artist ${i}`, youtubeVideoId: String(i).padStart(11, '0'), thumbnailUrl: null }))
test('pins retain exact positions across fresh feeds and remove duplicates', () => {
  const pins = { 4: songs[0], 8: songs[1] }
  const grid = pinnedGrid(songs.slice().reverse(), pins)
  assert.equal(grid.length, 9)
  assert.equal(grid[4], songs[0]); assert.equal(grid[8], songs[1])
  assert.equal(new Set(grid.map(song => song?.youtubeVideoId)).size, 9)
})
test('pin storage isolates accounts and rejects corrupt, duplicate and invalid slots', () => {
  const data = new Map([[pinKey('a'), JSON.stringify({ 0: songs[0], 4: songs[0], 9: songs[1], 2: { title: 'bad' } })]])
  const storage = { getItem: (key: string) => data.get(key) || null }
  assert.deepEqual(readPins(storage, 'a'), { 0: songs[0] })
  assert.deepEqual(readPins(storage, 'b'), {})
  assert.deepEqual(readPins({ getItem: () => '{broken' }, 'a'), {})
})
test('normalization rejects Lithium variants and artist aliases across pins and fresh tracks', () => {
  for (const title of ['Lithium', 'Lithium (Devonshire Mix)', 'Lithium [Remastered 2011]', 'Nirvana - Lithium (Official Music Video)', 'Lithium - Devonshire Mix']) assert.equal(titleKey(title), 'lithium')
  const lithium = { ...songs[0], title: 'Lithium', artist: 'Nirvana' }
  const variants = [{ ...songs[1], title: 'Lithium (Remastered)', artist: 'NirvanaVEVO' }, { ...songs[2], title: 'In Bloom', artist: 'Nirvana - Topic' }, { ...songs[3], title: 'Come As You Are', artist: 'Nirvana' }]
  const grid = pinnedGrid([...variants, ...songs.slice(4)], { 4: lithium }).filter(song => song !== null)
  assert.equal(grid.filter(song => titleKey(song.title) === 'lithium').length, 1)
  assert.equal(grid.filter(song => artistKey(song.artist) === 'nirvana').length, 2)
  assert.equal(grid.length, 9)
})
