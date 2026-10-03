import { useEffect, useRef, useState } from 'react'
import type { Song } from '../../services/api'
import { api, GOOGLE_LOGIN_URL } from '../../services/api'
import PlaylistDialog from '../PlaylistDialog'
import { useSwiped } from './SwipedContext'
import { usePlayer } from '../player/PlayerContext'
import { swipeDirection } from './storage'

export default function SwipeDeck({ onLike }: { onLike: (song: Song) => boolean }) {
  const [songs, setSongs] = useState<Song[]>([])
  const [index, setIndex] = useState(0)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [retry, setRetry] = useState(0)
  const [notice, setNotice] = useState('')
  const [drag, setDrag] = useState(0)
  const start = useRef<{ x: number; y: number; id: number } | null>(null)
  const player = usePlayer()
  const library = useSwiped()
  const [adding, setAdding] = useState<Song | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    api.trending(controller.signal).then(tracks => { if (!controller.signal.aborted) setSongs(tracks) })
      .catch((reason: unknown) => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Trending music unavailable.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [retry])
  const song = songs[index]
  function choose(like: boolean) {
    if (!song) return
    if (like && !onLike(song)) { setNotice('Connect Google to save this song to Swiped.'); return }
    setNotice(`${like ? 'Liked' : 'Passed'}: ${song.title}`); setIndex(value => value + 1); setDrag(0); start.current = null
  }
  return <div className="swipe-area" tabIndex={0} aria-label="Swipe music deck. Left arrow passes, right arrow likes." onKeyDown={event => {
    if ((event.target as HTMLElement).closest('button,input,textarea,select,a') || event.repeat) return
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); choose(event.key === 'ArrowRight') }
  }}>
    {adding && <PlaylistDialog videoId={adding.youtubeVideoId} onClose={() => setAdding(null)} />}
    <p className="deck-source">LIVE YOUTUBE MUSIC / US TRENDING</p>
    {loading ? <p role="status">LOADING THE NEXT LEVEL...</p> : error ? <div role="alert"><p>{error}</p><button className="pixel-button" onClick={() => { setLoading(true); setError(''); setIndex(0); setRetry(value => value + 1) }}>RETRY</button></div> : song ? <>
      <div className="trading-card pixel-panel" style={{ transform: `translateX(${Math.max(-100, Math.min(100, drag))}px) rotate(${drag / 28}deg)` }}
        onPointerDown={event => { if ((event.target as HTMLElement).closest('button') || event.button !== 0) return; start.current = { x: event.clientX, y: event.clientY, id: event.pointerId }; event.currentTarget.setPointerCapture(event.pointerId) }}
        onPointerMove={event => { if (start.current?.id === event.pointerId) setDrag(event.clientX - start.current.x) }}
        onPointerUp={event => { const origin = start.current; if (!origin || origin.id !== event.pointerId) return; const direction = swipeDirection(event.clientX - origin.x, event.clientY - origin.y); start.current = null; setDrag(0); if (direction) choose(direction === 'like') }}
        onPointerCancel={() => { start.current = null; setDrag(0) }}>
        <div className="cartridge-label">MUSIC CARD #{String(index + 1).padStart(2, '0')} <span>{index + 1}/{songs.length}</span></div>
        {song.thumbnailUrl && <img draggable={false} src={song.thumbnailUrl} alt="" />}
        <h3>{song.title}</h3><p>{song.artist}</p><button className="pixel-button" onClick={() => player.play(song)}>PREVIEW TRACK</button>
      </div>
      <div className="swipe-controls"><button className="pixel-button pass-button" onClick={() => choose(false)}>X PASS</button><button className="pixel-button like-button" onClick={() => choose(true)}>+ LIKE</button><button className="pixel-button secondary" aria-label={'Add ' + song.title + ' to playlist'} onClick={() => { if (library.userId) setAdding(song); else window.location.href = GOOGLE_LOGIN_URL }}>+ PLAYLIST</button></div>
      <p className="deck-hint">Drag left or right, use the buttons, or focus the deck and press arrow keys.</p>
    </> : <div><p>{songs.length ? 'DECK COMPLETE. Nice listening!' : 'No playable trending tracks available.'}</p><button className="pixel-button" onClick={() => { setLoading(true); setError(''); setIndex(0); setRetry(value => value + 1) }}>REFRESH DECK</button></div>}
    <p className="swipe-notice" aria-live="polite">{notice}</p>
  </div>
}

