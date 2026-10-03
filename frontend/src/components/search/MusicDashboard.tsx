import { useState } from 'react'
import { api, ApiError } from '../../services/api'
import type { Profile, Song } from '../../services/api'
import ImportedPlaylists from '../ImportedPlaylists'
import RetroSearchBar from './RetroSearchBar'
import SongCard from './SongCard'

interface Props { profile: Profile; onPlay: (song: Song, queue?: Song[], playlistId?: string) => void }
export default function MusicDashboard({ profile, onPlay }: Props) {
  const [mode, setMode] = useState<'ai' | 'normal'>('ai')
  const [searched, setSearched] = useState(false)
  const [songs, setSongs] = useState<Song[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  async function search(prompt: string) {
    setLoading(true); setError(''); setSearched(true)
    try { setSongs((await (mode === 'ai' ? api.recommend(prompt) : api.directSearch(prompt))).slice(0, 3)) }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Could not curate tracks. Please try again.') }
    finally { setLoading(false) }
  }
  return <>
    <RetroSearchBar loading={loading} onSearch={search} mode={mode} />
    <div className="search-modes" role="group" aria-label="Search mode">
      <span className={'mode-indicator ' + (mode === 'normal' ? 'normal' : '')} aria-hidden="true" />
      <button type="button" aria-pressed={mode === 'ai'} disabled={loading} onClick={() => setMode('ai')}>AI VIBE</button>
      <button type="button" aria-pressed={mode === 'normal'} disabled={loading} onClick={() => setMode('normal')}>NORMAL SEARCH</button>
    </div>
    <p className="mode-help">{mode === 'ai' ? "Describe a vibe, mood, or setting (e.g. 'midnight drive through Tokyo')." : 'Direct song/artist search via YouTube Music (bypasses AI).'}</p>
    {loading && <div className="curating" role="status"><span className="pixel-spinner" aria-hidden="true" />{mode === 'ai' ? 'CURATING SOUNDTRACK...' : 'SEARCHING MUSIC...'}</div>}
    {error && <p className="error-message" role="alert">{error}</p>}
    <section className="recommendations" aria-label="Recommended songs" aria-busy={loading}>
      {songs.length > 0 ? <><div className="section-heading"><h2>YOUR SOUNDTRACK</h2><span>3 SLOTS / ENDLESS VIBES</span></div><div className="song-grid">{songs.map((song, index) => <SongCard key={song.youtubeVideoId} song={song} index={index} onPlay={track => onPlay(track)} />)}</div></> : !loading && <p className="empty-message">{searched ? 'No matching songs found. Try another song or artist.' : mode === 'ai' ? 'Try "midnight city drive" or "sunny afternoon jazz".' : 'Search for a song title or artist.'}</p>}
    </section>
    {profile.authenticated && profile.id && <ImportedPlaylists userId={profile.id} />}
  </>
}
