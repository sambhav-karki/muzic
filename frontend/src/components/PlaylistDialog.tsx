import { useEffect, useState } from 'react'
import type { SubmitEvent } from 'react'
import { api } from '../services/api'
import type { YouTubePlaylist } from '../services/api'
import PixelDialog from './PixelDialog'
export default function PlaylistDialog({ videoId, onClose }: { videoId?: string; onClose: () => void }) {
  const [items, setItems] = useState<YouTubePlaylist[]>([])
  const [selected, setSelected] = useState('new')
  const [title, setTitle] = useState('')
  const [loading, setLoading] = useState(Boolean(videoId))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState<YouTubePlaylist | null>(null)
  useEffect(() => {
    if (!videoId) return
    const controller = new AbortController()
    api.playlists(controller.signal).then(setItems).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not load playlists.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [videoId])
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const playlist = selected === 'new' ? created || await api.createPlaylist(title.trim()) : items.find(item => item.id === selected)
      if (!playlist) throw new Error('Choose a playlist.')
      if (selected === 'new') setCreated(playlist)
      window.dispatchEvent(new Event('youtube-playlists-changed'))
      if (videoId) await api.addToPlaylist(playlist.id, videoId)
      window.dispatchEvent(new Event('youtube-playlists-changed')); onClose()
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not update playlist.') }
    finally { setBusy(false) }
  }
  return <PixelDialog className="playlist-dialog pixel-panel" label={videoId ? 'Add to YouTube playlist' : 'Create YouTube playlist'} onClose={() => { if (!busy) onClose() }}><form onSubmit={event => void submit(event)}>
    <h2>{videoId ? 'ADD TO PLAYLIST' : 'CREATE PLAYLIST'}</h2><p>Your YouTube playlists ? new playlists are private.</p>
    {loading && <p role="status">Loading playlists...</p>}
    {videoId && <label>Playlist<select aria-label="Playlist" value={selected} disabled={busy || loading} onChange={event => setSelected(event.target.value)}><option value="new">+ Create new playlist</option>{items.map(item => <option value={item.id} key={item.id}>{item.title}</option>)}</select></label>}
    {selected === 'new' && <label>Title<input autoFocus required maxLength={150} value={created?.title || title} disabled={busy || Boolean(created)} onChange={event => setTitle(event.target.value)} /></label>}
    {created && error && <p>Playlist created. Retry to add the track.</p>}{error && <p className="error-message" role="alert">{error}</p>}
    <div className="dialog-actions"><button type="button" className="pixel-button secondary" disabled={busy} onClick={onClose}>CANCEL</button><button className="pixel-button" disabled={busy || loading || (selected === 'new' && !created && !title.trim())}>{busy ? 'SAVING...' : videoId ? 'ADD TRACK' : 'CREATE'}</button></div>
  </form></PixelDialog>
}
