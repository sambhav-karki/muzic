import type { Song } from '../../services/api.ts'
import { selectionGuard } from './identity.ts'

export type Pins = Record<number, Song>
export const pinKey = (userId: string) => 'muzic-pins:' + userId
export function readPins(storage: Pick<Storage, 'getItem'>, userId: string): Pins {
  try {
    const value: unknown = JSON.parse(storage.getItem(pinKey(userId)) || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    const pins: Pins = {}, accept = selectionGuard()
    for (const [slot, track] of Object.entries(value)) {
      const song = track as Song | null
      if (!/^[0-8]$/.test(slot) || !song || typeof song.title !== 'string' || !song.title.trim() || typeof song.artist !== 'string'
        || typeof song.youtubeVideoId !== 'string' || !/^[A-Za-z0-9_-]{11}$/.test(song.youtubeVideoId) || !accept(song)) continue
      pins[Number(slot)] = { ...song, thumbnailUrl: typeof song.thumbnailUrl === 'string' ? song.thumbnailUrl : null }
    }
    return pins
  } catch { return {} }
}
export function pinnedGrid(tracks: Song[], pins: Pins): (Song | null)[] {
  const accept = selectionGuard(), grid: (Song | null)[] = Array(9).fill(null)
  for (let slot = 0; slot < 9; slot++) if (pins[slot] && accept(pins[slot])) grid[slot] = pins[slot]
  const pool = tracks.filter(accept)
  return grid.map(song => song || pool.shift() || null)
}
