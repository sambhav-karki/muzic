import { useEffect, useRef, useState } from 'react'
import type { Profile } from '../services/api'
import { API_BASE } from '../services/api'
import PixelDialog from './PixelDialog'

export default function ProfileMenu({ profile }: { profile: Profile }) {
  const [open, setOpen] = useState(false)
  const [friends, setFriends] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    menu.current?.querySelector<HTMLButtonElement>('button')?.focus()
    const dismiss = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', dismiss)
    return () => document.removeEventListener('pointerdown', dismiss)
  }, [open])
  function close() { setOpen(false); trigger.current?.focus() }
  return <div className="profile-menu" ref={root} onBlur={event => {
    if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget)) setOpen(false)
  }}>
    <button ref={trigger} className="status-chip profile-trigger" aria-label="User menu" aria-haspopup="menu" aria-expanded={open} aria-controls="profile-options" onClick={() => setOpen(value => !value)}>
      {profile.pictureUrl ? <img src={profile.pictureUrl} alt="" referrerPolicy="no-referrer" /> : <span className="profile-avatar" aria-hidden="true">{(profile.name || 'P').slice(0, 1).toUpperCase()}</span>}
      <span className="profile-name">{profile.name || 'PLAYER CONNECTED'}</span><svg viewBox="0 0 12 8" width="12" height="8" aria-hidden="true"><path d="M1 1h3v3h4V1h3v3H8v3H4V4H1Z" fill="currentColor" /></svg>
    </button>
    {open && <div className="profile-options pixel-panel" ref={menu} id="profile-options" role="menu" aria-label="Account options" onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); close() }
      const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button'))
      const current = buttons.indexOf(document.activeElement as HTMLButtonElement)
      if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
        event.preventDefault()
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
        buttons[next]?.focus()
      }
    }}>
      <button role="menuitem" onClick={() => { close(); setFriends(true) }}>Add friends</button>
      <form method="post" action={API_BASE + '/logout'}><button role="menuitem" type="submit">Logout</button></form>
    </div>}
    {friends && <PixelDialog className="friends-dialog pixel-panel" label="Add friends" onClose={() => { setFriends(false); trigger.current?.focus() }}>
      <header><h2>Add friends</h2><button className="player-close" aria-label="Close Add friends" onClick={() => setFriends(false)}>X</button></header>
      <p>Coming soon, Sam is working on it</p><div className="dialog-actions"><button className="pixel-button" onClick={() => setFriends(false)}>OK</button></div>
    </PixelDialog>}
  </div>
}
