import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { Song } from '../../services/api'
import { readSwiped, storageKey } from './storage'

interface LibraryState {
  songs: Song[]; error: string; userId: string | null
  setUserId: (id: string | null) => void
  like: (song: Song) => boolean
  remove: (ids: Set<string>) => void
}
const Context = createContext<LibraryState | null>(null)
export function SwipedProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(null)
  const [songs, setSongs] = useState<Song[]>([])
  const [error, setError] = useState('')
  function changeUser(id: string | null) {
    if (id === userId) return
    setUserId(id)
    try { setSongs(id ? readSwiped(localStorage, id) : []); setError('') }
    catch { setSongs([]); setError('Browser storage is unavailable.') }
  }
  useEffect(() => {
    const sync = (event: StorageEvent) => {
      if (userId && event.key === storageKey(userId)) setSongs(readSwiped(localStorage, userId))
    }
    window.addEventListener('storage', sync)
    return () => window.removeEventListener('storage', sync)
  }, [userId])
  function persist(next: Song[]) {
    if (!userId) return
    setSongs(next)
    try { localStorage.setItem(storageKey(userId), JSON.stringify(next)); setError('') }
    catch { setError('Browser storage is unavailable. Changes last only until this page closes.') }
  }
  return <Context.Provider value={{ songs, error, userId, setUserId: changeUser,
    like: song => { if (!userId) return false; if (!songs.some(item => item.youtubeVideoId === song.youtubeVideoId)) persist([...songs, song]); return true },
    remove: ids => persist(songs.filter(song => !ids.has(song.youtubeVideoId))),
  }}>{children}</Context.Provider>
}
// oxlint-disable-next-line react/only-export-components
export function useSwiped() {
  const value = useContext(Context)
  if (!value) throw new Error('useSwiped requires SwipedProvider')
  return value
}
