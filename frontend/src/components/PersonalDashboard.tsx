import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../services/api'
import type { DashboardFeed, YouTubePlaylist } from '../services/api'
import { usePlayer } from './player/PlayerContext'
import { useSwiped } from './discovery/SwipedContext'
import SongCard from './search/SongCard'
import { pinKey, pinnedGrid, readPins } from './discovery/pins'
import type { Pins } from './discovery/pins'
import VibeSwipeDeck from './discovery/VibeSwipeDeck'

function Shelf({ title, children }: { title: string; children: ReactNode }) {
  const row = useRef<HTMLDivElement>(null)
  return <section className="music-shelf"><header><h2>{title}</h2><div><button className="pixel-button secondary" aria-label={'Previous ' + title} onClick={() => row.current?.scrollBy({ left: -row.current.clientWidth, behavior: 'smooth' })}>[ &lt; ]</button><button className="pixel-button secondary" aria-label={'Next ' + title} onClick={() => row.current?.scrollBy({ left: row.current.clientWidth, behavior: 'smooth' })}>[ &gt; ]</button></div></header><div className="shelf-track" ref={row}>{children}</div></section>
}
export default function PersonalDashboard({ userId }: { userId: string }) {
  const [feed, setFeed] = useState<DashboardFeed | null>(null)
  const [pins, setPins] = useState<Pins>(() => { try { return readPins(localStorage, userId) } catch { return {} } })
  const [pinError, setPinError] = useState('')
  const slots = pinnedGrid([...(feed?.quickPicks || []), ...(feed?.quickPickPool || [])], pins)
  function togglePin(slot: number) {
    const song = slots[slot]
    if (!song) return
    const next = { ...pins }
    if (next[slot]) delete next[slot]; else next[slot] = song
    setPins(next)
    try { localStorage.setItem(pinKey(userId), JSON.stringify(next)); setPinError('') }
    catch { setPinError('Pins are saved for this session only because browser storage is unavailable.') }
  }
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [opening, setOpening] = useState('')
  const player = usePlayer()
  const library = useSwiped()
  useEffect(() => {
    const controller = new AbortController()
    api.feed(controller.signal).then(value => { if (!controller.signal.aborted) setFeed(value) }).catch(reason => { if (!controller.signal.aborted) setError(reason.message) })
    return () => controller.abort()
  }, [attempt])
  async function openPlaylist(playlist: YouTubePlaylist) {
    setOpening(playlist.id); setError('')
    try { const songs = await api.playlistItems(playlist.id); if (songs.length) player.play(songs[0], songs, playlist.id); else setError('This playlist has no playable tracks.') }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not load playlist.') }
    finally { setOpening('') }
  }
  const retry = () => { setError(''); setAttempt(value => value + 1) }
  const visibleIds = new Set(slots.filter(song => song !== null).map(song => song.youtubeVideoId))
  return <div className="personal-dashboard"><div className="dashboard-intro"><span className="eyebrow">YOUR PERSONAL MUZE</span><h1>Welcome to your frequency.</h1><p>Your favorites. New discoveries. Always in tune.</p></div>
    {error && <p role="alert">{error} <button className="pixel-button secondary" onClick={retry}>RETRY</button></p>}
    {!feed && !error && <p role="status">TUNING YOUR STATION...</p>}
    {(feed || Object.keys(pins).length > 0) && <section className="quick-picks" aria-label="Quick Picks"><header className="quick-heading"><div><h2>QUICK PICKS</h2><small>Primary Anchor: {feed?.primaryAnchor || 'Loading your library...'}</small></div><button className="pixel-button secondary" onClick={retry}>REFRESH PICKS</button></header>
      {pinError && <p role="status">{pinError}</p>}
      <div className="quick-picks-grid">{slots.map((song, slot) => <div className={'quick-slot ' + (pins[slot] ? 'pinned' : '')} key={slot} data-slot={slot}>{song ? <><SongCard song={song} onPlay={player.play} index={slot} /><button className="pin-track pixel-button secondary" aria-label={(pins[slot] ? 'Unpin ' : 'Pin ') + song.title} aria-pressed={Boolean(pins[slot])} onClick={() => togglePin(slot)}><svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M5 2h6M6 2v5L4 9v2h8V9l-2-2V2M8 11v4" /></svg></button></> : <p className="quick-empty">Waiting for a matching track</p>}</div>)}</div>
    </section>}
    {feed && <>
      {feed.musicError && <p role="status">{feed.musicError} <button className="pixel-button secondary" onClick={retry}>RETRY</button></p>}
      <Shelf title="FORGOTTEN FAVORITES">{feed.forgottenFavorites.filter(song => !visibleIds.has(song.youtubeVideoId)).map(song => <SongCard key={song.youtubeVideoId} song={song} onPlay={player.play} />)}{!feed.forgottenFavorites.some(song => !visibleIds.has(song.youtubeVideoId)) && <p className="shelf-empty">Older favorites will appear here as your library grows.</p>}</Shelf>
      <Shelf title="TOP GLOBAL HITS">{feed.globalHits.map(song => <SongCard key={song.youtubeVideoId} song={song} onPlay={player.play} />)}{!feed.globalHits.length && <p className="shelf-empty">Global music is temporarily unavailable.</p>}</Shelf>
      <VibeSwipeDeck />
      <Shelf title="YOUR PLAYLISTS & SWIPED"><button className="playlist-card pixel-panel" disabled={!library.songs.length} onClick={() => player.play(library.songs[0], library.songs, 'swiped')}><span className="playlist-cover">â™¥</span><strong>Swiped</strong><small>{library.songs.length} saved tracks</small></button>{feed.userPlaylists.map(playlist => <button className="playlist-card pixel-panel" key={playlist.id} disabled={Boolean(opening)} onClick={() => void openPlaylist(playlist)}>{playlist.thumbnailUrl ? <img src={playlist.thumbnailUrl} alt="" /> : <span className="playlist-cover">â™«</span>}<strong>{playlist.title}</strong><small>{opening === playlist.id ? 'LOADING...' : `${playlist.itemCount} tracks Â· YouTube`}</small></button>)}</Shelf>
      {feed.playlistError && <p role="status">{feed.playlistError} <button className="pixel-button secondary" onClick={retry}>RETRY</button></p>}
    </>}
  </div>
}
