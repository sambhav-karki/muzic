import { useEffect, useRef, useState } from 'react'
import { api, backendReadiness } from '../../services/api'
import type { Profile, Song } from '../../services/api'
import { connectGoogle } from '../../services/api'
import RetroSearchBar from './RetroSearchBar'
import SongCard from './SongCard'

interface Props { profile: Profile; onPlay: (song: Song, queue?: Song[], playlistId?: string) => void; compact?: boolean }
export default function MusicDashboard({ profile, onPlay, compact = false }: Props) {
  const [mode, setMode] = useState<'ai' | 'normal'>('ai')
  const [searched, setSearched] = useState(false)
  const [songs, setSongs] = useState<Song[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [pages, setPages] = useState(1)
  const tokens = useRef<(string | undefined)[]>([undefined])
  const [hasNext, setHasNext] = useState(false)
  const pending = useRef<AbortController | null>(null)
  useEffect(() => () => pending.current?.abort(), [])
  async function search(prompt: string, targetPage = 0) {
    pending.current?.abort()
    const controller = new AbortController()
    pending.current = controller
    setLoading(true); setError(''); setSearched(true)
    try {
      await backendReadiness.ensureReady(controller.signal)
      const result = mode === 'ai' ? { tracks: await api.recommend(prompt, controller.signal), totalPages: 1, nextPageToken: null } : await api.search(prompt, targetPage, tokens.current[targetPage], controller.signal)
      if (!controller.signal.aborted) {
        setSongs(result.tracks); setQuery(prompt); setPage(targetPage); setPages(result.totalPages)
        if (targetPage === 0) tokens.current = [undefined]
        tokens.current[targetPage + 1] = result.nextPageToken || undefined
        setHasNext(result.nextPageToken === null ? targetPage + 1 < result.totalPages : Boolean(result.nextPageToken))
      }
    } catch (reason) {
      if (!controller.signal.aborted && !(reason instanceof Error && reason.name === 'AbortError')) {
        setError(reason instanceof Error ? reason.message : 'Could not curate tracks. Please try again.')
      }
    } finally {
      if (pending.current === controller) { pending.current = null; setLoading(false) }
    }
  }
  return <>
    <div className={'hub-controls ' + (compact ? 'compact' : '')}><RetroSearchBar loading={loading} onSearch={search} mode={mode} />
    <div className="search-modes" role="group" aria-label="Search mode">
      <span className={'mode-indicator ' + (mode === 'normal' ? 'normal' : '')} aria-hidden="true" />
      <button type="button" aria-label="AI VIBE" aria-pressed={mode === 'ai'} disabled={loading} onClick={() => { setMode('ai'); setSongs([]); setPage(0) }}>{compact ? 'AI' : 'AI VIBE'}</button>
      <button type="button" aria-label="NORMAL SEARCH" aria-pressed={mode === 'normal'} disabled={loading} onClick={() => { setMode('normal'); setSongs([]); setPage(0) }}>{compact ? <svg width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M3 3h9v9H3zM12 12l6 6" /></svg> : 'NORMAL SEARCH'}</button>
    </div></div>
    {!compact && <p className="mode-help">{mode === 'ai' ? "Describe a vibe, mood, or setting (e.g. 'midnight drive through Tokyo')." : 'Direct song/artist search via YouTube Music (bypasses AI).'}</p>}
    {!profile.authenticated && !compact && <div className="personal-connect"><button className="pixel-button" onClick={() => void connectGoogle()}>[ &gt; YOUR PERSONAL MUZE &lt; ]</button><small>Connect Google Account for Personalized Picks, Playlists &amp; YouTube Sync</small></div>}
    {loading && <div className="curating" role="status"><span className="pixel-spinner" aria-hidden="true" />{mode === 'ai' ? 'CURATING SOUNDTRACK...' : 'SEARCHING MUSIC...'}</div>}
    {error && <p className="error-message" role="alert">{error}</p>}
    {(songs.length > 0 || searched) && <section className="recommendations" aria-label="Recommended songs" aria-busy={loading}>
      {songs.length > 0 ? <><div className="section-heading"><h2>YOUR SOUNDTRACK</h2><span>ENDLESS VIBES</span></div><div className="song-grid">{songs.map((song, index) => <SongCard key={song.youtubeVideoId} song={song} index={index} onPlay={track => onPlay(track)} />)}</div></> : !loading && <p className="empty-message">No matching songs found. Try another song or artist.</p>}
    </section>}
    {mode === 'normal' && songs.length > 0 && <nav className="search-pagination" aria-label="Search pages"><button className="pixel-button secondary" disabled={loading || page === 0} onClick={() => void search(query, page - 1)}>[ &lt; PREV ]</button><span aria-live="polite">PAGE {page + 1} OF {pages}</span><button className="pixel-button secondary" disabled={loading || !hasNext} onClick={() => void search(query, page + 1)}>[ NEXT &gt; ]</button></nav>}
  </>
}
