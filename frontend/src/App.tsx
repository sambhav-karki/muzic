import { useEffect, useState } from 'react'
import SplashIntro from './components/SplashIntro'
import MusicDashboard from './components/search/MusicDashboard'
import { api, API_BASE, GUEST_PROFILE, onUnauthorized } from './services/api'
import BackendWakeOverlay from './components/BackendWakeOverlay'
import type { Profile } from './services/api'
import { PlayerProvider, usePlayer } from './components/player/PlayerContext'
import RetroPlayer from './components/player/RetroPlayer'
import PersonalDashboard from './components/PersonalDashboard'
import { SwipedProvider, useSwiped } from './components/discovery/SwipedContext'
import ProfileMenu from './components/ProfileMenu'
import PrivacyPolicy from './pages/PrivacyPolicy'
import TermsOfService from './pages/TermsOfService'
import './App.css'
import './stages.css'
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
  const [profile, setProfile] = useState<Profile>(GUEST_PROFILE)
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
    // Restore a session quietly; no health probe, wake loop, or landing-page error.
    api.profile(controller.signal)
      .then(profile => { if (!controller.signal.aborted && !authFailed) setProfile(profile) })
      .catch(() => { if (!controller.signal.aborted) setProfile(GUEST_PROFILE) })
    return () => { controller.abort(); unsubscribe() }
  }, [])
  const [searchOpen, setSearchOpen] = useState(false)
  useEffect(() => {
    document.body.classList.toggle('guest-hub', !profile.authenticated)
    return () => document.body.classList.remove('guest-hub')
  }, [profile.authenticated])
  return <><SplashIntro />{toast && <div className="toast pixel-panel" role="status">{toast}</div>}
    <div className={'app-shell ' + (profile.authenticated ? 'authenticated-shell' : 'guest-shell')}>
      <header className="site-header">
        {profile.authenticated ? <ProfileMenu profile={profile} /> : <span className="status-chip">SYSTEM ONLINE</span>}
        <a className="brand" href="#home">Muzic<span>[]</span></a>
        <div className="header-controls">
          {profile.authenticated && <button className="pixel-button secondary" aria-label="Search music" aria-expanded={searchOpen} onClick={() => setSearchOpen(value => !value)}><svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M3 3h9v9H3zM12 12l6 6" /></svg></button>}
          <button className="pixel-button secondary theme-toggle" aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'} aria-pressed={theme === 'light'} onClick={() => setTheme(value => value === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? 'LIGHT' : 'DARK'}</button>
          {profile.authenticated && <form method="post" action={API_BASE + '/logout'}><button className="pixel-button secondary">LOG OUT</button></form>}
        </div>
      </header>
      <main id="home">
        {profile.authenticated ? <><div className={'dashboard-search ' + (searchOpen ? 'expanded' : '')} inert={!searchOpen}>{searchOpen && <MusicDashboard profile={profile} onPlay={player.play} />}</div><PersonalDashboard /></> : <section className="hero-deck"><h1 className="muze-headline" data-text="Muze away">Muze away</h1><p>Tell us your mood. Discover your next favorite song.</p><MusicDashboard profile={profile} onPlay={player.play} /></section>}
      </main>
      <footer className="site-footer pixel-text"><span>MUZIC BY SAM</span><span>BUILT FOR THE LOVE OF MUSIC</span><nav className="legal-links" aria-label="Legal"><a href="/terms">[ Terms of Service ]</a><a href="/privacy">[ Privacy Policy ]</a></nav></footer>
    </div><RetroPlayer />
  </>
}

export default function App() {
  // Legal documents stay public and never mount backend-dependent player/session views.
  const path = window.location.pathname.replace(/\/+$/, '') || '/'
  if (path === '/privacy') return <PrivacyPolicy />
  if (path === '/terms') return <TermsOfService />
  return <SwipedProvider><PlayerProvider><Dashboard /><BackendWakeOverlay /></PlayerProvider></SwipedProvider>
}



