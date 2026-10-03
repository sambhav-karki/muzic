import { useEffect, useRef, useState } from 'react'
import { api } from '../services/api'
import type { Song, YouTubePlaylist } from '../services/api'
import PixelDialog from './PixelDialog'
import PlaylistDialog from './PlaylistDialog'
import { usePlayer } from './player/PlayerContext'
import { uniqueSongs } from './player/queue'

type Imported = { id: string; title: string; songs: Song[] }
export default function ImportedPlaylists({ userId }: { userId: string }) {
  const key = 'muzic-imported:' + userId
  const [imports, setImports] = useState<Imported[]>(() => {
    try {
      const value: unknown = JSON.parse(localStorage.getItem(key) || '[]')
      return Array.isArray(value) ? value.filter(p => typeof p.id === 'string' && typeof p.title === 'string' && Array.isArray(p.songs)).map(p => ({ ...p, songs: uniqueSongs(p.songs.filter((s: Song) => s && typeof s.youtubeVideoId === 'string' && typeof s.title === 'string' && typeof s.artist === 'string')) })).filter(p => p.songs.length > 0) : []
    } catch { return [] }
  })
  const [creating, setCreating] = useState(false)
  const [open, setOpen] = useState(false)
  const [playlists, setPlaylists] = useState<YouTubePlaylist[]>([])
  const [loading, setLoading] = useState(false)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState('')
  const [active, setActive] = useState<Imported | null>(null)
  const request = useRef<AbortController | null>(null)
  const player = usePlayer()
  useEffect(() => () => request.current?.abort(), [])
  function close() { request.current?.abort(); setOpen(false); setImporting(false) }
  async function show() {
    request.current?.abort()
    const controller = new AbortController(); request.current = controller
    setOpen(true); setLoading(true); setError('')
    try { const result = await api.playlists(controller.signal); if (!controller.signal.aborted) setPlaylists(result) }
    catch (reason) { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not load playlists.') }
    finally { if (!controller.signal.aborted) setLoading(false) }
  }
  async function importPlaylist(playlist: YouTubePlaylist) {
    const controller = new AbortController(); request.current = controller
    setImporting(true); setError('')
    try {
      const songs = uniqueSongs(await api.playlistItems(playlist.id, controller.signal))
      if (controller.signal.aborted) return
      if (!songs.length) throw new Error('This playlist has no available tracks to import.')
      const imported = { id: playlist.id, title: playlist.title, songs }
      const next = [...imports.filter(p => p.id !== playlist.id), imported]
      localStorage.setItem(key, JSON.stringify(next))
      setImports(next); setOpen(false); setActive(imported)
    } catch (reason) { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not import tracks.') }
    finally { if (!controller.signal.aborted) setImporting(false) }
  }
  function play(song: Song, playlist: Imported) { setActive(null); player.play(song, playlist.songs, 'imported:' + playlist.id) }
  return <section className="saved-playlists">
    <div className="section-heading"><h2>YOUR PLAYLIST TRAY</h2><button className="pixel-button import-button" onClick={show}>IMPORT PLAYLIST<svg aria-hidden="true" viewBox="0 0 100 40" preserveAspectRatio="none"><rect x="1" y="1" width="98" height="38" pathLength="100" /></svg></button></div>
    {creating && <PlaylistDialog onClose={() => setCreating(false)} />}
    <button className="pixel-button secondary" onClick={() => setCreating(true)}>CREATE PLAYLIST</button>
    <div className="playlist-grid">{imports.map(playlist => <button key={playlist.id} className="playlist-card pixel-panel" onClick={() => setActive(playlist)}>{playlist.songs[0]?.thumbnailUrl && <img src={playlist.songs[0].thumbnailUrl} alt="" />}<strong>{playlist.title}</strong><small className="imported-tag">Imported / {playlist.songs.length} TRACKS</small></button>)}</div>
    {open && <PixelDialog className="playlist-dialog pixel-panel" label="Import YouTube playlist" onClose={close}>
      <h2>IMPORT PLAYLIST</h2><button className="player-close" aria-label="Close import playlist" onClick={close}>X</button>
      {(loading || importing) && <div className="curating" role="status"><span className="pixel-spinner" aria-hidden="true" />{importing ? 'IMPORTING TRACKS...' : 'LOADING PLAYLISTS...'}</div>}
      {error && <p className="error-message" role="alert">{error}</p>}
      {!loading && !importing && <div className="import-choices">{playlists.map(playlist => <button className="playlist-choice" key={playlist.id} onClick={() => importPlaylist(playlist)}>{playlist.thumbnailUrl && <img src={playlist.thumbnailUrl} alt="" />}<strong>{playlist.title}</strong><small>{playlist.itemCount} TRACKS</small></button>)}{!playlists.length && !error && <p>No YouTube playlists available.</p>}</div>}
    </PixelDialog>}
    {active && <PixelDialog className="playlist-dialog pixel-panel" label={active.title + ' tracks'} onClose={() => setActive(null)}>
      <h2>{active.title}</h2><button className="player-close" aria-label="Close imported playlist" onClick={() => setActive(null)}>X</button>
      <div className="dialog-actions"><button className="pixel-button" onClick={() => { player.setShuffle(false); play(active.songs[0], active) }}>PLAY ALL</button><button className="pixel-button secondary" onClick={() => { player.setShuffle(true); play(active.songs[Math.floor(Math.random() * active.songs.length)], active) }}>SHUFFLE</button></div>
      <div className="import-choices">{active.songs.map(song => <button className="playlist-choice" key={song.youtubeVideoId} onClick={() => play(song, active)}><strong>{song.title}</strong><small>{song.artist}</small></button>)}</div>
    </PixelDialog>}
  </section>
}
