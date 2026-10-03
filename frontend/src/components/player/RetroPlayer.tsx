import { useEffect, useRef } from 'react'
import { usePlayer } from './PlayerContext'
import { useState } from 'react'
import { useSwiped } from '../discovery/SwipedContext'
import PixelDialog from '../PixelDialog'
import './RetroPlayer.css'

interface YoutubePlayer {
  getCurrentTime: () => number; getDuration: () => number; seekTo: (seconds: number, allowSeekAhead: boolean) => void
  loadVideoById: (id: string) => void; playVideo: () => void; pauseVideo: () => void
  setVolume: (volume: number) => void; destroy: () => void
  getVideoData: () => { video_id?: string }; getIframe: () => HTMLIFrameElement
}
interface YoutubeEvent { target: YoutubePlayer; data: number }
interface YoutubeApi { Player: new (element: HTMLElement, config: {
  width: number; height: number; playerVars: Record<string, string | number>
  events: { onReady: (event: YoutubeEvent) => void; onStateChange: (event: YoutubeEvent) => void; onError: (event: YoutubeEvent) => void; onAutoplayBlocked: () => void }
}) => YoutubePlayer }
declare global { interface Window { YT?: YoutubeApi; onYouTubeIframeAPIReady?: () => void } }
let youtubePromise: Promise<YoutubeApi> | null = null
function loadYoutube(): Promise<YoutubeApi> {
  if (window.YT?.Player) return Promise.resolve(window.YT)
  if (!youtubePromise) youtubePromise = new Promise((resolve, reject) => {
    const previous = window.onYouTubeIframeAPIReady
    const timeout = window.setTimeout(() => { youtubePromise = null; reject(new Error('YouTube took too long to load. Reload to retry.')) }, 15000)
    window.onYouTubeIframeAPIReady = () => { previous?.(); window.clearTimeout(timeout); if (window.YT) resolve(window.YT) }
    const script = document.createElement('script'); script.src = 'https://www.youtube.com/iframe_api'
    script.onerror = () => { window.clearTimeout(timeout); youtubePromise = null; reject(new Error('YouTube could not load. Check your connection.')) }
    document.head.appendChild(script)
  })
  return youtubePromise
}

export default function RetroPlayer() {
  const player = usePlayer()
  const library = useSwiped()
  const [minimized, setMinimized] = useState(false)
  const [progress, setProgress] = useState({ time: 0, duration: 0 })
  const latest = useRef(player)
  useEffect(() => { latest.current = player }, [player])
  const host = useRef<HTMLDivElement>(null)
  const instance = useRef<YoutubePlayer | null>(null)
  const loadedId = useRef('')
  const loadedKey = useRef(-1)
  const hasSong = Boolean(player.song)
  useEffect(() => {
    if (!hasSong || !host.current) return
    let cancelled = false
    latest.current.setMessage('CONNECTING TO YOUTUBE...')
    const mount = document.createElement('div'); host.current.appendChild(mount)
    loadYoutube().then(YT => {
      if (cancelled) return
      instance.current = new YT.Player(mount, {
        width: 1, height: 1, playerVars: { playsinline: 1, origin: window.location.origin, controls: 0, disablekb: 1 },
        events: {
          onReady: ({ target }) => {
            if (cancelled) return
            const state = latest.current; target.setVolume(state.volume)
            target.getIframe().title = 'Hidden music audio engine'
            target.getIframe().tabIndex = -1
            target.getIframe().setAttribute('aria-hidden', 'true')
            if (state.song) { loadedId.current = state.song.youtubeVideoId; loadedKey.current = state.playbackKey; target.loadVideoById(state.song.youtubeVideoId); if (!state.playing) target.pauseVideo() }
          },
          onStateChange: ({ data, target }) => {
            if (cancelled || loadedKey.current !== latest.current.playbackKey || target.getVideoData().video_id !== latest.current.song?.youtubeVideoId) return
            if (data === 0) { latest.current.setPlaying(false); void latest.current.next() }
            if (data === 1) { latest.current.setPlaying(true); latest.current.setMessage('') }
            if (data === 2) latest.current.setPlaying(false)
          },
          onError: ({ data, target }) => { if (!cancelled && loadedKey.current === latest.current.playbackKey && (!target.getVideoData().video_id || target.getVideoData().video_id === latest.current.song?.youtubeVideoId)) { latest.current.setPlaying(false); latest.current.setMessage(`YouTube cannot play this track (${data}). Select Next to try another.`) } },
          onAutoplayBlocked: () => { if (!cancelled) { latest.current.setPlaying(false); latest.current.setMessage('Press Play to start playback.') } },
        },
      })
    }).catch((error: unknown) => { if (!cancelled) latest.current.setMessage(error instanceof Error ? error.message : 'YouTube unavailable.') })
    return () => { cancelled = true; instance.current?.destroy(); instance.current = null; loadedId.current = ''; mount.remove() }
  }, [hasSong])
  useEffect(() => {
    const yt = instance.current
    if (!yt?.loadVideoById || !player.song || !loadedId.current) return
    if (loadedKey.current !== player.playbackKey) { loadedId.current = player.song.youtubeVideoId; loadedKey.current = player.playbackKey; yt.loadVideoById(loadedId.current) }
    else if (player.playing) yt.playVideo()
    else yt.pauseVideo()
  }, [player.song, player.playing, player.playbackKey])
  useEffect(() => { if (loadedId.current) instance.current?.setVolume(player.volume) }, [player.volume])
  useEffect(() => {
    if (!hasSong) return
    const timer = window.setInterval(() => {
      const yt = instance.current
      if (loadedId.current && yt?.getDuration) setProgress({ time: yt.getCurrentTime(), duration: yt.getDuration() })
    }, 500)
    return () => window.clearInterval(timer)
  }, [hasSong])
  if (!player.song) return null
  const liked = library.songs.some(song => song.youtubeVideoId === player.song?.youtubeVideoId)
  const clock = (seconds: number) => `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`
  const controls = <div className="player-controls"><button className="pixel-button secondary" onClick={player.previous} aria-label="Previous track">|&lt;</button><button className="pixel-button" onClick={player.toggle} aria-label={player.playing ? 'Pause' : 'Play'}>{player.playing ? 'II' : '▶'}</button><button className="pixel-button secondary" onClick={() => void player.next()} disabled={player.busy} aria-label="Next track">&gt;|</button><button className="pixel-button secondary" disabled={player.busy} onClick={() => void player.shuffleTrack()} aria-label="Shuffle" aria-pressed={player.playlistMode ? player.shuffle : undefined}>⇄</button></div>
  return <><div className="audio-engine" ref={host} aria-hidden="true" />{minimized ? <section className="player-dock pixel-panel" aria-label="Minimized music player">
    <button className="dock-title" onClick={() => setMinimized(false)} aria-label="Restore music player"><strong>{player.song.title}</strong><small>{player.song.artist}</small></button>
    {controls}<button className="dock-art" onClick={() => setMinimized(false)} aria-label="Restore player from thumbnail">{player.song.thumbnailUrl ? <img src={player.song.thumbnailUrl} alt="" /> : <span>♫</span>}</button>
    {player.message && <span className="dock-message" role="status">{player.message}</span>}
  </section> : <PixelDialog className="retro-player pixel-panel" label="Music player" onClose={player.stop}>
    <div className="player-top"><span className="pixel-text">MUZIC // POCKET DECK</span><div className="player-window-controls"><button className="player-close" onClick={() => setMinimized(true)} aria-label="Minimize player">_</button><button className="player-close" onClick={player.stop} aria-label="Stop and close player">X</button></div></div>
    <div className="player-console">
      <div className="player-lcd"><span>{player.playing ? '> NOW PLAYING' : 'II PAUSED'}</span><div className="lcd-marquee"><h2>{player.song.title}</h2><p>{player.song.artist}</p></div><small>{player.playlistMode ? player.shuffle ? 'SHUFFLE ON' : 'PLAYLIST MODE' : 'VIBE MODE'}</small></div>
      <div className={`pixel-cassette ${player.playing ? 'spinning' : ''}`} aria-hidden="true"><span>✳</span><div>MUZIC<br />SIDE A · HIGH FIDELITY</div><span>✳</span></div>
      <label className="player-progress">TRACK POSITION<input aria-label="Track position" type="range" min="0" max={progress.duration || 0} step="1" value={Math.min(progress.time, progress.duration)} disabled={!progress.duration} onChange={event => { const time = Number(event.target.value); instance.current?.seekTo(time, true); setProgress(value => ({ ...value, time })) }} /><span>{clock(progress.time)} / {clock(progress.duration)}</span></label>
      {controls}
      <button className="pixel-button player-like" disabled={liked} aria-pressed={liked} onClick={() => { if (player.song && !library.like(player.song)) player.setMessage('Connect Google to save tracks in Swiped.') }}>{liked ? '♥ IN SWIPED' : '♡ ADD TO SWIPED'}</button>
      <label className="player-volume">VOLUME <input type="range" min="0" max="100" value={player.volume} onChange={event => player.setVolume(Number(event.target.value))} /><span>{player.volume}%</span></label>
      {player.message && <p className="player-message" role="status">{player.message}</p>}
      {library.error && <p className="player-message" role="status">{library.error}</p>}
    </div>
  </PixelDialog>}</>
}


