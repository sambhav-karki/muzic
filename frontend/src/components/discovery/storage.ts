import type { Song } from '../../services/api'
export const storageKey = (id: string) => `muzic:swiped:v1:${encodeURIComponent(id)}`
export function readSwiped(storage: Pick<Storage, 'getItem'>, id: string): Song[] {
  try {
    const value: unknown = JSON.parse(storage.getItem(storageKey(id)) || '[]')
    if (!Array.isArray(value)) return []
    const seen = new Set<string>()
    return value.filter((song): song is Song => {
      if (!song || typeof song !== 'object' || typeof song.title !== 'string' || typeof song.artist !== 'string'
        || typeof song.youtubeVideoId !== 'string' || !/^[\w-]{11}$/.test(song.youtubeVideoId)
        || (song.thumbnailUrl !== null && typeof song.thumbnailUrl !== 'string') || seen.has(song.youtubeVideoId)) return false
      seen.add(song.youtubeVideoId); return true
    })
  } catch { return [] }
}
export function swipeDirection(dx: number, dy: number): 'like' | 'pass' | null {
  return Math.abs(dx) >= 65 && Math.abs(dx) > Math.abs(dy) * 1.3 ? dx > 0 ? 'like' : 'pass' : null
}
