import { useSyncExternalStore } from 'react'
import { backendReadiness } from '../services/api'
import PixelDialog from './PixelDialog'
import './BackendWakeOverlay.css'

function WakeTerminal() {
  return <PixelDialog label="Backend waking up" className="wake-terminal" onClose={backendReadiness.cancel}>
    <button className="wake-close" aria-label="Close wake-up popup" onClick={backendReadiness.cancel}>[X]</button>
    <div className="wake-screen" role="status" aria-live="polite">
      <p>&gt; SYSTEM STATUS: WAKING CONTAINER...</p>
      <p>&gt; Free tier deployment in use [No budget for AWS]</p>
      <p>&gt; Don't mind Render's cold start...</p>
      <p className="wake-progress">&gt; [■■■■■□□□□□] INITIALIZING TOMCAT</p>
      <p>&gt; The server hit snooze. Brewing digital coffee.</p>
      <p>&gt; Your request will resume automatically.<span className="wake-cursor" aria-hidden="true">_</span></p>
    </div>
    <p className="wake-help">Checking Render every 3.5 seconds. Close [X] to cancel your request.</p>
  </PixelDialog>
}

export default function BackendWakeOverlay() {
  const isBackendWakingUp = useSyncExternalStore(backendReadiness.subscribe, backendReadiness.getSnapshot)
  return isBackendWakingUp ? <WakeTerminal /> : null
}
