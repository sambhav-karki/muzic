import { useState } from 'react'
import PlaylistDialog from '../PlaylistDialog'
import { useSwiped } from '../discovery/SwipedContext'
import { GOOGLE_LOGIN_URL } from '../../services/api'
import type { Song } from '../../services/api'

export default function SongCard({ song, index = 0, onPlay }: { song: Song; index?: number; onPlay: (song: Song) => void }) {
  const library = useSwiped()
  const [adding, setAdding] = useState(false)
  const liked = library.songs.some(track => track.youtubeVideoId === song.youtubeVideoId)
  const connect = () => { window.location.href = GOOGLE_LOGIN_URL }
  return <article className="track-card pixel-panel">
    {adding && <PlaylistDialog videoId={song.youtubeVideoId} onClose={() => setAdding(false)} />}
    <button className="song-card" onClick={() => onPlay(song)} aria-label={`Play ${song.title} by ${song.artist}`}>
    <div className="cartridge-label"><span>TRACK {String(index + 1).padStart(2, '0')}</span><span>▶</span></div>
    <div className="song-art">{song.thumbnailUrl ? <img src={song.thumbnailUrl} alt="" loading="lazy" onError={event => { event.currentTarget.style.display = 'none' }} /> : null}<span className="art-fallback" aria-hidden="true">♫</span></div>
    <div className="cassette-reels" aria-hidden="true">◉ ━━━━━ ◉</div>
    <h3>{song.title}</h3><p>{song.artist}</p><span className="card-play pixel-text">PRESS TO PLAY</span>
  </button>
    <div className="track-actions"><button className="pixel-button secondary" disabled={liked} aria-pressed={liked} aria-label={liked ? song.title + ' in Swiped' : 'Like ' + song.title} onClick={() => { if (!library.like(song)) connect() }}>{liked ? 'IN SWIPED' : 'LIKE'}</button><button className="pixel-button secondary" aria-label={'Add ' + song.title + ' to playlist'} onClick={() => { if (library.userId) setAdding(true); else connect() }}>+ PLAYLIST</button></div>
  </article>
}
