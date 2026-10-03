import { useEffect, useState } from 'react'
import type { Profile, Song } from '../../services/api'
import { api, GOOGLE_LOGIN_URL } from '../../services/api'
import { usePlayer } from '../player/PlayerContext'
import SongCard from '../search/SongCard'
import SwipeDeck from './SwipeDeck'
import { useSwiped } from './SwipedContext'
import SwipedLibrary from './SwipedLibrary'
import './discovery.css'

function PersonalLibrary({ userId }: { userId: string }) {
  const { songs: liked, like } = useSwiped()
  const [feed, setFeed] = useState<Song[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [retry, setRetry] = useState(0)
  const player = usePlayer()
  useEffect(() => {
    if (!liked.length) return
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true); setError(''); setFeed([])
      const prompt = `Recommend music based on these favorites: ${liked.slice(-5).map(song => `${song.artist} - ${song.title}`).join('; ')}`.slice(0, 900)
      api.recommend(prompt, controller.signal).then(songs => {
        if (!controller.signal.aborted) setFeed(songs.filter(song => !liked.some(item => item.youtubeVideoId === song.youtubeVideoId)))
      }).catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not curate your feed.')
      }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    }, 700)
    return () => { window.clearTimeout(timer); controller.abort() }
  }, [liked, retry])
  return <><SwipeDeck onLike={like} /><SwipedLibrary userId={userId} /><section className="personal-feed"><div className="eyebrow">BONUS ROUND / INSPIRED BY YOUR LIKES</div><h3>Your next favorites</h3>
    {!liked.length ? <p>Your likes will shape the tracks you discover here.</p> : loading ? <p role="status">CURATING YOUR PERSONAL FEED...</p> : error ? <div role="alert"><p className="error-message">{error}</p><button className="pixel-button" onClick={() => setRetry(value => value + 1)}>RETRY FEED</button></div> : feed.length ? <div className="song-grid">{feed.map((song, index) => <SongCard key={song.youtubeVideoId} song={song} index={index} onPlay={player.play} />)}</div> : <div><p>No new matches this round. Like more songs or try again.</p><button className="pixel-button" onClick={() => setRetry(value => value + 1)}>TRY AGAIN</button></div>}
  </section></>
}
export default function DiscoveryLounge({ profile }: { profile: Profile | null }) {
  return <><div className="pixel-divider" aria-hidden="true"><span>♪</span><i /><span>DISCOVERY LOADING</span><i /><span>♪</span></div><section className="discovery-lounge"><div className="eyebrow">LEVEL 02 / DISCOVERY LOUNGE</div><h2>Swipe That Song!</h2><p>New tracks. Quick decisions. Find your next obsession.</p>
    {profile?.authenticated && profile.id ? <PersonalLibrary key={profile.id} userId={profile.id} /> : <><SwipeDeck onLike={() => false} /><p className="guest-likes"><a href={GOOGLE_LOGIN_URL}>Connect Google</a> to save likes in your personal, website-only Swiped playlist.</p></>}
  </section></>
}


