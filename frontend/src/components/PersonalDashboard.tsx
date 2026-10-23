import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../services/api'
import type { Song, YouTubePlaylist } from '../services/api'
import { usePlayer } from './player/PlayerContext'
import { useSwiped } from './discovery/SwipedContext'
import SongCard from './search/SongCard'

function Shelf({ title, children }: { title: string; children: ReactNode }) {
  const row = useRef<HTMLDivElement>(null)
  return <section className="music-shelf"><header><h2>{title}</h2><div><button className="pixel-button secondary" aria-label={'Previous ' + title} onClick={() => row.current?.scrollBy({ left: -row.current.clientWidth, behavior: 'smooth' })}>[ &lt; ]</button><button className="pixel-button secondary" aria-label={'Next ' + title} onClick={() => row.current?.scrollBy({ left: row.current.clientWidth, behavior: 'smooth' })}>[ &gt; ]</button></div></header><div className="shelf-track" ref={row}>{children}</div></section>
}
export default function PersonalDashboard() {
  const [feed, setFeed] = useState<{ quickPicks: Song[]; trending: Song[]; userPlaylists: YouTubePlaylist[]; playlistError?: string; musicError?: string } | null>(null)
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
  return <div className="personal-dashboard"><div className="dashboard-intro"><span className="eyebrow">YOUR PERSONAL MUZE</span><h1>Welcome to your frequency.</h1><p>Your favorites. New discoveries. Always in tune.</p></div>
    {error && <p role="alert">{error} <button className="pixel-button secondary" onClick={() => setAttempt(value => value + 1)}>RETRY</button></p>}
    {!feed && !error && <p role="status">TUNING YOUR STATION...</p>}
    {feed && <>
      {feed.musicError && <p role="status">{feed.musicError} <button className="pixel-button secondary" onClick={() => setAttempt(value => value + 1)}>RETRY</button></p>}
      <Shelf title="QUICK PICKS / MIXES">{feed.quickPicks.map(song => <SongCard key={song.youtubeVideoId} song={song} onPlay={player.play} />)}</Shelf>
      <Shelf title="FORGOTTEN FAVORITES & TOP HITS">{[...library.songs, ...feed.trending.filter(song => !library.songs.some(saved => saved.youtubeVideoId === song.youtubeVideoId))].map(song => <SongCard key={song.youtubeVideoId} song={song} onPlay={player.play} />)}</Shelf>
      <Shelf title="YOUR YOUTUBE PLAYLISTS & SWIPED"><button className="playlist-card pixel-panel" disabled={!library.songs.length} onClick={() => player.play(library.songs[0], library.songs, 'swiped')}><span className="playlist-cover">♥</span><strong>Swiped</strong><small>{library.songs.length} saved tracks</small></button>{feed.userPlaylists.map(playlist => <button className="playlist-card pixel-panel" key={playlist.id} disabled={Boolean(opening)} onClick={() => void openPlaylist(playlist)}>{playlist.thumbnailUrl ? <img src={playlist.thumbnailUrl} alt="" /> : <span className="playlist-cover">♫</span>}<strong>{playlist.title}</strong><small>{opening === playlist.id ? 'LOADING...' : `${playlist.itemCount} tracks · YouTube`}</small></button>)}{[...library.songs, ...feed.trending.filter(song => !library.songs.some(saved => saved.youtubeVideoId === song.youtubeVideoId))].slice(0, Math.max(0, 8 - feed.userPlaylists.length)).map(song => <div className="shelf-discovery" key={song.youtubeVideoId}><small>{library.songs.some(saved => saved.youtubeVideoId === song.youtubeVideoId) ? 'FROM SWIPED' : 'EXPLORE MUSIC'}</small><SongCard song={song} onPlay={player.play} /></div>)}</Shelf>
      {feed.playlistError && <p role="status">{feed.playlistError} <button className="pixel-button secondary" onClick={() => setAttempt(value => value + 1)}>RETRY</button></p>}
    </>}
  </div>
}
