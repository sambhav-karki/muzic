import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import './SplashIntro.css'
const INTRO_KEY = 'muzic:intro-seen'
function hasSeenIntro() { try { return sessionStorage.getItem(INTRO_KEY) === '1' } catch { return false } }
export default function SplashIntro() {
  const [phase, setPhase] = useState<'muzic' | 'sam' | 'burst' | 'done'>(() => hasSeenIntro() ? 'done' : 'muzic')
  useEffect(() => {
    if (hasSeenIntro()) return
    const finish = () => { try { sessionStorage.setItem(INTRO_KEY, '1') } catch { /* Storage can be unavailable. */ } setPhase('done') }
    const timers = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? [window.setTimeout(finish, 100)] : [window.setTimeout(() => setPhase('sam'), 1000), window.setTimeout(() => setPhase('burst'), 2100), window.setTimeout(finish, 3000)]
    return () => timers.forEach(window.clearTimeout)
  }, [])
  if (phase === 'done') return null
  return <div className={`splash-intro phase-${phase}`} role="status" aria-label="Muzic by Sam"><div className="splash-emblem" aria-hidden="true">[]</div><div className="splash-title" aria-hidden="true"><span className="splash-muzic">Muzic</span><span className="splash-sam">By Sam</span></div><span className="splash-caption pixel-text" aria-hidden="true">LOADING GOOD VIBES...</span><div className="splash-particles" aria-hidden="true">{Array.from({length:40},(_,i) => { const angle=i*Math.PI*2/40; return <i key={i} style={{'--dx':`${Math.cos(angle)*(180+i%5*45)}px`,'--dy':`${Math.sin(angle)*(160+i%7*30)}px`,'--color':['#00ffcc','#ff007f','#ffe600'][i%3],'--delay':`${i%4*25}ms`} as CSSProperties} /> })}</div></div>
}
