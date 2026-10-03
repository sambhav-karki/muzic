import type { Song } from '../../services/api'

export default function SongCard({ song, index = 0, onPlay }: { song: Song; index?: number; onPlay: (song: Song) => void }) {
  return <button className="song-card pixel-panel" onClick={() => onPlay(song)} aria-label={`Play ${song.title} by ${song.artist}`}>
    <div className="cartridge-label"><span>TRACK {String(index + 1).padStart(2, '0')}</span><span>▶</span></div>
    <div className="song-art">{song.thumbnailUrl ? <img src={song.thumbnailUrl} alt="" loading="lazy" onError={event => { event.currentTarget.style.display = 'none' }} /> : null}<span className="art-fallback" aria-hidden="true">♫</span></div>
    <div className="cassette-reels" aria-hidden="true">◉ ━━━━━ ◉</div>
    <h3>{song.title}</h3><p>{song.artist}</p><span className="card-play pixel-text">PRESS TO PLAY</span>
  </button>
}
