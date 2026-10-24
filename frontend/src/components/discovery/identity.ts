import type { Song } from '../../services/api.ts'
export function titleKey(title: string): string {
  return title.normalize('NFKD').toLowerCase().replaceAll('&amp;', 'and').replaceAll('&#39;', '').replaceAll('&quot;', '')
    .replace(/\([^)]*(?:remaster|deluxe|live|mix|version|edit|official|audio|lyric)[^)]*\)|\[[^\]]*(?:remaster|deluxe|live|mix|version|edit|official|audio|lyric)[^\]]*\]/g, ' ')
    .replace(/\s[-–—]\s[^–—-]*(?:mix|remaster|live|version|deluxe|edit).*$/g, ' ').replace(/^.*?\s[-–—]\s/, '')
    .replace(/\b(?:official(?: music)? video|lyrics?(?: video)?|official audio|audio|remastered?|deluxe(?: edition)?|\d{4} remaster(?:ed)?)\b/g, ' ')
    .replace(/\b(?:19|20)\d{2}\b/g, ' ').replace(/[^\p{L}\p{N} ]/gu, '').replace(/\s+/g, ' ').trim()
}
export const artistKey = (artist: string) => artist.normalize('NFKD').toLowerCase().replace(/(?:vevo|official|topic)\b/g, '').replace(/[^\p{L}\p{N}]/gu, '')
export function selectionGuard() {
  const ids = new Set<string>(), titles = new Set<string>(), artists = new Map<string, number>()
  return (song: Song) => {
    const title = titleKey(song.title), artist = artistKey(song.artist)
    if (!title || !artist || ids.has(song.youtubeVideoId) || titles.has(title) || (artists.get(artist) || 0) >= 2) return false
    ids.add(song.youtubeVideoId); titles.add(title); artists.set(artist, (artists.get(artist) || 0) + 1); return true
  }
}
