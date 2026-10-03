import { useState } from 'react'
import { useSwiped } from './SwipedContext'
import { usePlayer } from '../player/PlayerContext'
import PixelDialog from '../PixelDialog'
import type { Song } from '../../services/api'

export default function SwipedLibrary({ userId }: { userId: string }) {
  const { songs, error, remove } = useSwiped()
  const player = usePlayer()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState('title')
  const [selecting, setSelecting] = useState(false)
  const [selected, setSelected] = useState(new Set<string>())
  const visible = songs.filter(song => `${song.title} ${song.artist}`.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => sort === 'artist' ? a.artist.localeCompare(b.artist) || a.title.localeCompare(b.title) : a.title.localeCompare(b.title))
  const count = songs.filter(song => selected.has(song.youtubeVideoId)).length
  const allSelected = visible.length > 0 && visible.every(song => selected.has(song.youtubeVideoId))
  function toggle(id: string) { setSelected(current => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next }) }
  function play(song: Song) { setOpen(false); player.play(song, songs, `swiped:${userId}`) }
  const track = (song: Song) => <button className="swiped-track" onClick={() => play(song)}><span aria-hidden="true">PLAY</span><strong>{song.title}</strong><small>{song.artist}</small></button>
  return <section className="swiped-library"><div className="section-heading"><h3>Swiped <span>{songs.length}</span></h3><span className="website-only">WEBSITE ONLY / NEVER SYNCED TO YOUTUBE</span><button className="pixel-button" onClick={() => setOpen(true)}>SHOW ALL</button></div>
    {error && <p role="status" className="error-message">{error}</p>}
    {songs.length ? <div className="swiped-list">{songs.slice(0, 3).map(song => <div key={song.youtubeVideoId}>{track(song)}</div>)}</div> : <p>Like a card to build your own Swiped playlist on this browser.</p>}
    {open && <PixelDialog className="library-dialog pixel-panel" label="Swiped library" onClose={() => setOpen(false)}>
      <header className="library-header"><div><div className="eyebrow">YOUR PERSONAL MIXTAPE</div><h2>SWIPED / {songs.length}</h2></div><button className="player-close" aria-label="Close Swiped library" onClick={() => setOpen(false)}>X</button></header>
      <div className="library-tools"><label>SEARCH TRACKS<input type="search" placeholder="Title or artist..." value={query} onChange={event => setQuery(event.target.value)} /></label><label>SORT<select value={sort} onChange={event => setSort(event.target.value)}><option value="title">Alphabetical (A-Z)</option><option value="artist">Artist grouping</option></select></label><button className="pixel-button secondary" aria-pressed={selecting} onClick={() => { setSelecting(value => !value); setSelected(new Set()) }}>Select Tracks</button></div>
      {selecting && <div className="library-batch"><label><input type="checkbox" checked={allSelected} disabled={!visible.length} onChange={() => setSelected(current => { const next = new Set(current); visible.forEach(song => { if (allSelected) next.delete(song.youtubeVideoId); else next.add(song.youtubeVideoId) }); return next })} /> Select All</label><span>{count} SELECTED</span><button className="pixel-button pink" disabled={!count} aria-label="Remove selected tracks" onClick={() => { remove(selected); setSelected(new Set()) }}>REMOVE SELECTED</button></div>}
      {error && <p role="status" className="error-message">{error}</p>}
      {!visible.length && <p className="library-empty">{songs.length ? 'NO MATCHES. TRY ANOTHER SEARCH.' : 'YOUR MIXTAPE IS EMPTY. LIKE A TRACK TO START.'}</p>}
      <div className="library-grid">{visible.map((song, index) => <div key={song.youtubeVideoId}>
        {sort === 'artist' && (index === 0 || visible[index - 1].artist !== song.artist) && <h3 className="artist-group">{song.artist}</h3>}
        <article className={`library-card ${selected.has(song.youtubeVideoId) ? 'selected' : ''}`}>
          {selecting && <label className="track-checkbox"><input type="checkbox" aria-label={`Select ${song.title}`} checked={selected.has(song.youtubeVideoId)} onChange={() => toggle(song.youtubeVideoId)} /> SELECT</label>}
          {song.thumbnailUrl && <img src={song.thumbnailUrl} alt="" loading="lazy" />}{track(song)}
        </article></div>)}</div>
    </PixelDialog>}
  </section>
}
