import { useEffect, useState } from 'react'
import SplashIntro from './components/SplashIntro'
import MusicDashboard from './components/search/MusicDashboard'
import { api, GOOGLE_LOGIN_URL, GUEST_PROFILE, onUnauthorized } from './services/api'
import type { Profile } from './services/api'
import { PlayerProvider, usePlayer } from './components/player/PlayerContext'
import RetroPlayer from './components/player/RetroPlayer'
import DiscoveryLounge from './components/discovery/DiscoveryLounge'
import { SwipedProvider, useSwiped } from './components/discovery/SwipedContext'
import ProfileMenu from './components/ProfileMenu'
import './App.css'
function Dashboard() {
  const [theme, setTheme] = useState<'dark' | 'light'>(() => { try { return localStorage.getItem('muzic-theme') === 'light' ? 'light' : 'dark' } catch { return 'dark' } })
  useEffect(() => { document.body.classList.toggle('theme-light', theme === 'light'); document.body.classList.toggle('theme-dark', theme === 'dark'); try { localStorage.setItem('muzic-theme', theme) } catch { /* Theme works without storage. */ } }, [theme])
  const [toast, setToast] = useState('')
  useEffect(() => {
    let timer: number | undefined
    const notify = (event: Event) => {
      setToast((event as CustomEvent<string>).detail)
      window.clearTimeout(timer)
      timer = window.setTimeout(() => setToast(''), 3500)
    }
    window.addEventListener('muzic-toast', notify)
    return () => { window.removeEventListener('muzic-toast', notify); window.clearTimeout(timer) }
  }, [])
  const [profile, setProfile] = useState<Profile | null>(null)
  const [profileError, setProfileError] = useState('')
  const player = usePlayer()
  const { setUserId } = useSwiped()
  useEffect(() => { setUserId(profile?.authenticated ? profile.id : null) }, [profile, setUserId])
  useEffect(() => {
    const controller = new AbortController()
    let authFailed = false
    const unsubscribe = onUnauthorized(() => {
      authFailed = true
      setProfile(GUEST_PROFILE)
    })
    api.authStatus(controller.signal)
      .then(status => status.authenticated ? api.profile(controller.signal) : GUEST_PROFILE)
      .then(profile => { if (!controller.signal.aborted && !authFailed) setProfile(profile) }).catch((reason: unknown) => {
      if (!controller.signal.aborted) {
        setProfile(GUEST_PROFILE)
        setProfileError(reason instanceof Error ? reason.message : 'Could not check account status.')
      }
    })
    return () => { controller.abort(); unsubscribe() }
  }, [])
  return <><SplashIntro />{toast && <div className="toast pixel-panel" role="status">{toast}</div>}<div className="app-shell"><header className="site-header"><button className="pixel-button secondary theme-toggle" aria-label="Toggle light theme" aria-pressed={theme === 'light'} onClick={() => setTheme(value => value === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? 'LIGHT' : 'DARK'}</button><a className="brand" href="#home">Muzic<span>[]</span></a>{profile?.authenticated ? <ProfileMenu profile={profile} /> : <span className="status-chip">SYSTEM ONLINE</span>}</header><main id="home"><section className="hero-deck"><h1 className="muze-headline" data-text="Your personal Muze">Your personal Muze</h1><p>Tell us your mood. Discover your next favorite song.</p>
    {profile?.authenticated === false && <a className="account-banner pixel-panel" href={GOOGLE_LOGIN_URL} onClick={event => { event.preventDefault(); window.location.href = GOOGLE_LOGIN_URL }}><span aria-hidden="true">+</span> Connect your google account to unlock more features <span aria-hidden="true">&gt;</span></a>}
    {profileError && <p className="error-message" role="status">{profileError} Refresh to check your account again.</p>}
    <MusicDashboard key={profile?.id || 'guest'} profile={profile || { authenticated: false, id: null, name: null, pictureUrl: null }} onPlay={player.play} />
    <RetroPlayer />
  </section><DiscoveryLounge profile={profile} /></main><footer className="site-footer pixel-text">MUZIC BY SAM <span>BUILT FOR THE LOVE OF MUSIC</span></footer></div></>
}

export default function App() { return <SwipedProvider><PlayerProvider><Dashboard /></PlayerProvider></SwipedProvider> }



