import { useEffect, useState } from 'react'
import SplashIntro from './components/SplashIntro'
import MusicDashboard from './components/search/MusicDashboard'
import { api, GOOGLE_LOGIN_URL } from './services/api'
import type { Profile } from './services/api'
import { PlayerProvider, usePlayer } from './components/player/PlayerContext'
import RetroPlayer from './components/player/RetroPlayer'
import DiscoveryLounge from './components/discovery/DiscoveryLounge'
import { SwipedProvider, useSwiped } from './components/discovery/SwipedContext'
import './App.css'
function Dashboard() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [profileError, setProfileError] = useState('')
  const player = usePlayer()
  const { setUserId } = useSwiped()
  useEffect(() => { setUserId(profile?.authenticated ? profile.id : null) }, [profile, setUserId])
  useEffect(() => {
    const controller = new AbortController()
    api.profile(controller.signal).then(setProfile).catch((reason: unknown) => {
      if (!controller.signal.aborted) setProfileError(reason instanceof Error ? reason.message : 'Could not check account status.')
    })
    return () => controller.abort()
  }, [])
  return <><SplashIntro /><div className="app-shell"><header className="site-header"><a className="brand" href="#home">Muzic<span>♪</span></a><span className="header-tag pixel-text">INSERT VIBE · PRESS PLAY</span><span className="status-chip">■ {profile?.authenticated ? profile.name || 'PLAYER CONNECTED' : 'SYSTEM ONLINE'}</span></header><main id="home"><section className="hero-deck"><div className="eyebrow">LEVEL 01 / YOUR PERSONAL SOUNDTRACK</div><h1>Good vibes.<br /><span>Great tracks.</span></h1><p>Tell us your mood. Discover your next favorite song.</p>
    {profile?.authenticated === false && <a className="account-banner pixel-panel" href={GOOGLE_LOGIN_URL}><span aria-hidden="true">+</span> Connect your google account to unlock more features <span aria-hidden="true">↗</span></a>}
    {profileError && <p className="error-message" role="status">{profileError} Refresh to check your account again.</p>}
    <MusicDashboard key={profile?.id || 'guest'} profile={profile || { authenticated: false, id: null, name: null, pictureUrl: null }} onPlay={player.play} />
    <RetroPlayer key={player.song ? 'active' : 'idle'} />
  </section><DiscoveryLounge profile={profile} /></main><footer className="site-footer pixel-text">MUZIC BY SAM <span>BUILT FOR THE LOVE OF MUSIC</span></footer></div></>
}

export default function App() { return <SwipedProvider><PlayerProvider><Dashboard /></PlayerProvider></SwipedProvider> }



