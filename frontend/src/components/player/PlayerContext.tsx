import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../../services/api'
import type { Song } from '../../services/api'
import { planNext, uniqueSongs } from './queue'
import { useSwiped } from '../discovery/SwipedContext'

interface PlayerState {
  minimized: boolean; setMinimized: (value: boolean) => void; playbackKey: number; song: Song | null; playing: boolean; shuffle: boolean; volume: number; busy: boolean; message: string; playlistMode: boolean
  play: (song: Song, queue?: Song[], playlistId?: string) => void
  next: () => Promise<void>; previous: () => void; toggle: () => void; stop: () => void
  shuffleTrack: () => Promise<void>
  setShuffle: (value: boolean) => void; setVolume: (value: number) => void
  setPlaying: (value: boolean) => void; setMessage: (value: string) => void
}
const Context = createContext<PlayerState | null>(null)
export function PlayerProvider({ children }: { children: ReactNode }) {
  const library = useSwiped()
  const [minimized, setMinimized] = useState(true)
  const [playbackKey, setPlaybackKey] = useState(0)
  const [song, setSong] = useState<Song | null>(null)
  const [playing, setPlaying] = useState(false)
  const [playlistMode, setPlaylistMode] = useState(false)
  const [shuffle, setShuffle] = useState(false)
  const [volume, setVolume] = useState(70)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const session = useRef({ queue: [] as Song[], index: 0, playlist: false, seen: new Set<string>() })
  const request = useRef<AbortController | null>(null)
  const cancel = useCallback(() => { request.current?.abort(); request.current = null; setBusy(false) }, [])
  useEffect(() => () => request.current?.abort(), [])
  const play = useCallback((track: Song, queue?: Song[], playlistId?: string) => {
    cancel()
    const tracks = uniqueSongs(queue?.length ? queue : [track])
    if (!tracks.some(item => item.youtubeVideoId === track.youtubeVideoId)) tracks.unshift(track)
    session.current = { queue: tracks, index: tracks.findIndex(item => item.youtubeVideoId === track.youtubeVideoId), playlist: playlistId !== undefined, seen: new Set([track.youtubeVideoId]) }
    setPlaylistMode(playlistId !== undefined)
    setPlaybackKey(value => value + 1); setSong(track); setPlaying(true); setMessage('')
  }, [cancel])
  const next = useCallback(async () => {
    if (!song || request.current) return
    const state = session.current
    const index = planNext(state.index, state.queue.length, state.playlist, shuffle)
    if (index !== null) {
      state.index = index
      const track = state.queue[state.index]
      state.seen.add(track.youtubeVideoId); setPlaybackKey(value => value + 1); setSong(track); setPlaying(true); setMessage(''); return
    }
    const controller = new AbortController(); request.current = controller; setBusy(true); setMessage('CURATING NEXT VIBE...')
    try {
      const tracks = uniqueSongs(await api.recommend(`Songs with the same vibe as ${song.artist} - ${song.title}`, controller.signal), state.seen)
      if (controller.signal.aborted || request.current !== controller) return
      if (!tracks.length) { setMessage('No new tracks found. Try another vibe.'); return }
      state.queue.push(...tracks); state.index += 1
      const track = state.queue[state.index]; state.seen.add(track.youtubeVideoId)
      setPlaybackKey(value => value + 1); setSong(track); setPlaying(true); setMessage('')
    } catch (error) {
      if (!controller.signal.aborted) setMessage(error instanceof Error ? error.message : 'Could not continue this vibe.')
    } finally {
      if (request.current === controller) { request.current = null; setBusy(false) }
    }
  }, [song, shuffle])
  const previous = useCallback(() => {
    cancel(); const state = session.current
    if (!state.queue.length) return
    state.index = state.playlist ? (state.index - 1 + state.queue.length) % state.queue.length : Math.max(0, state.index - 1)
    setPlaybackKey(value => value + 1); setSong(state.queue[state.index]); setPlaying(true); setMessage('')
  }, [cancel])
  const shuffleTrack = async () => {
    if (playlistMode) { setShuffle(value => !value); return }
    if (!song || request.current) return
    const controller = new AbortController(); request.current = controller; setBusy(true); setMessage('ROLLING A NEW TRACK...')
    try {
      const excluded = new Set([...library.songs.map(track => track.youtubeVideoId), song.youtubeVideoId])
      const tracks = uniqueSongs(await api.trending(controller.signal), excluded)
      if (controller.signal.aborted || request.current !== controller) return
      if (!tracks.length) { setMessage('No unswiped tracks available. Try a new search.'); return }
      play(tracks[Math.floor(Math.random() * tracks.length)])
    } catch (error) {
      if (!controller.signal.aborted) setMessage(error instanceof Error ? error.message : 'Could not shuffle.')
    } finally { if (request.current === controller) { request.current = null; setBusy(false) } }
  }
  const toggle = useCallback(() => { if (song) setPlaying(value => !value) }, [song])
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      const target = event.target
      if (event.code !== 'Space' || event.repeat || event.altKey || event.ctrlKey || event.metaKey || !song) return
      if (target instanceof HTMLElement && (target.closest('input, textarea, select, button, a, [role="button"]') || target.isContentEditable)) return
      if (document.querySelector('dialog[open]:not(.retro-player)')) return
      event.preventDefault(); toggle()
    }
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  }, [song, toggle])
  const stop = () => { cancel(); setSong(null); setPlaying(false); setPlaylistMode(false); setMessage('') }
  return <Context.Provider value={{ minimized, setMinimized, playbackKey, song, playing, shuffle, volume, busy, message, playlistMode, play, next, previous, shuffleTrack, toggle, stop, setShuffle, setVolume, setPlaying, setMessage }}>{children}</Context.Provider>
}
// oxlint-disable-next-line react/only-export-components
export function usePlayer() {
  const value = useContext(Context)
  if (!value) throw new Error('usePlayer requires PlayerProvider')
  return value
}

