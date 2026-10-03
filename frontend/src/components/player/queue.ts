import type { Song } from '../../services/api'

export function uniqueSongs(songs: Song[], excluded: Set<string> = new Set()): Song[] {
  const seen = new Set(excluded)
  return songs.filter(song => {
    if (!/^[\w-]{11}$/.test(song.youtubeVideoId) || seen.has(song.youtubeVideoId)) return false
    seen.add(song.youtubeVideoId)
    return true
  })
}

export function nextIndex(index: number, length: number, shuffle: boolean, random = Math.random): number {
  if (length <= 1) return 0
  return shuffle ? (index + 1 + Math.floor(random() * (length - 1))) % length : (index + 1) % length
}

export function planNext(index: number, length: number, playlist: boolean, shuffle: boolean): number | null {
  return playlist || index + 1 < length ? nextIndex(index, length, playlist && shuffle) : null
}
