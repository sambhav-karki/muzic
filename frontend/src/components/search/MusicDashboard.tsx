import { useEffect, useState } from 'react'
import { api, ApiError } from '../../services/api'
import type { Profile, Song } from '../../services/api'
import RetroSearchBar from './RetroSearchBar'
import SongCard from './SongCard'

interface Props { profile: Profile; onPlay: (song: Song, queue?: Song[], playlistId?: string) => void }
export default function MusicDashboard({ profile, onPlay }: Props) {
  const [songs, setSongs] = useState<Song[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [playlists, setPlaylists] = useState<Awaited<ReturnType<typeof api.playlists>>>([])
  const [playlistError, setPlaylistError] = useState('')
  const [playlistsLoading, setPlaylistsLoading] = useState(true)
  useEffect(() => {
    if (!profile.authenticated) return
    const controller = new AbortController()
    api.playlists(controller.signal).then(setPlaylists).catch((reason: unknown) => {
      if (!controller.signal.aborted) setPlaylistError(reason instanceof Error ? reason.message : 'Could not load playlists.')
    }).finally(() => { if (!controller.signal.aborted) setPlaylistsLoading(false) })
    return () => controller.abort()
  }, [profile.id, profile.authenticated])
  async function search(prompt: string) {
    setLoading(true); setError('')
    try { setSongs((await api.recommend(prompt)).slice(0, 3)) }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Could not curate tracks. Please try again.') }
    finally { setLoading(false) }
  }
  return <>
    <RetroSearchBar loading={loading} onSearch={search} />
    {loading && <div className="curating" role="status"><span className="pixel-spinner" aria-hidden="true" />CURATING SOUNDTRACK...</div>}
    {error && <p className="error-message" role="alert">{error}</p>}
    <section className="recommendations" aria-label="Recommended songs" aria-busy={loading}>
      {songs.length > 0 ? <><div className="section-heading"><h2>YOUR SOUNDTRACK</h2><span>3 SLOTS · ENDLESS VIBES</span></div><div className="song-grid">{songs.map((song, index) => <SongCard key={song.youtubeVideoId} song={song} index={index} onPlay={track => onPlay(track)} />)}</div></> : !loading && <p className="empty-message">Try “midnight city drive” or “sunny afternoon jazz”.</p>}
    </section>
    {profile.authenticated && <section className="saved-playlists"><div className="section-heading"><h2>SAVED PLAYLISTS</h2><span>YOUR COLLECTION</span></div>
      {playlistsLoading && <p role="status">LOADING COLLECTION...</p>}{playlistError && <p className="error-message" role="alert">{playlistError}</p>}
      {!playlistsLoading && !playlistError && playlists.length === 0 && <p className="empty-message">Your collection is ready for its first playlist.</p>}
      <div className="playlist-grid">{playlists.map(playlist => <button className="playlist-card pixel-panel" key={playlist.id} disabled={!playlist.songs.length} onClick={() => onPlay(playlist.songs[0], playlist.songs, playlist.id)}><span aria-hidden="true">▤</span><strong>{playlist.name}</strong><small>{playlist.songs.length} TRACKS · PLAY COLLECTION</small></button>)}</div>
    </section>}
  </>
}
