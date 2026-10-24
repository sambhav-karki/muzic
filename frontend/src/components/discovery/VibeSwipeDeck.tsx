import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api'
import type { Song } from '../../services/api'
import { titleKey } from './identity'
import { swipeDirection } from './storage'
import { useSwiped } from './SwipedContext'
import { usePlayer } from '../player/PlayerContext'
import './vibe-deck.css'

type Direction = 'close' | 'divergent'
interface Card { song: Song; close?: Card; divergent?: Card; pending?: Partial<Record<Direction, Promise<void>>>; errors?: Partial<Record<Direction, string>> }

export default function VibeSwipeDeck() {
  const library = useSwiped(), player = usePlayer()
  const [card, setCard] = useState<Card | null>(null)
  const [, redraw] = useState(0)
  const [anchor, setAnchor] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [drag, setDrag] = useState(0)
  const origin = useRef<{ x: number; y: number; id: number } | null>(null)
  const current = useRef<Card | null>(null)
  const session = useRef<{ id: string; controller: AbortController; ids: Set<string>; titles: Set<string> } | null>(null)

  async function prefetch(node: Card, depth = 2) {
    const state = session.current
    if (!state || state.controller.signal.aborted || depth === 0) return
    await Promise.all((['close', 'divergent'] as const).map(async direction => {
      if (!node[direction] && !node.pending?.[direction]) {
        node.pending ??= {}; node.errors ??= {}; delete node.errors[direction]
        node.pending[direction] = (async () => {
          try {
            for (let retry = 0; retry < 3; retry++) {
              const song = await api.swipeNext(state.id, node.song, direction, [...state.ids], state.controller.signal)
              if (state.controller.signal.aborted || session.current !== state) return
              const key = titleKey(song.title)
              if (state.ids.has(song.youtubeVideoId) || state.titles.has(key)) continue
              state.ids.add(song.youtubeVideoId); state.titles.add(key); node[direction] = { song }; return
            }
            throw new Error('No distinct tracks are ready in this direction. Retry to refill.')
          } catch (reason) {
            if (!state.controller.signal.aborted) node.errors![direction] = reason instanceof Error ? reason.message : 'Could not prepare the next card.'
          } finally {
            delete node.pending?.[direction]
            if (!state.controller.signal.aborted) redraw(value => value + 1)
          }
        })()
      }
      await node.pending?.[direction]
      const next = node[direction]
      if (next) await prefetch(next, depth - 1)
    }))
  }

  useEffect(() => {
    const controller = new AbortController()
    api.swipeSession(controller.signal).then(result => {
      if (controller.signal.aborted) return
      const first: Card = { song: result.seed }
      session.current = { id: result.sessionId, controller, ids: new Set([first.song.youtubeVideoId]), titles: new Set([titleKey(first.song.title)]) }
      current.current = first; setCard(first); setAnchor(result.anchorName); setError('')
      void prefetch(first)
    }).catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not start your vibe session.') })
    return () => { controller.abort(); session.current = null }
  }, [attempt])

  function choose(direction: Direction) {
    const node = current.current, next = node?.[direction]
    if (!node || !next) { setNotice('Preparing this direction. Please retry in a moment.'); return }
    if (direction === 'close' && !library.like(node.song)) { setNotice('Connect Google to save songs to Swiped.'); return }
    setNotice(`${direction === 'close' ? 'Saved to Swiped' : 'Passed'}: ${node.song.title}`)
    current.current = next; setCard(next); setDrag(0); origin.current = null
    void prefetch(next)
  }
  const ready = card ? [card.close, card.divergent, card.close?.close, card.close?.divergent, card.divergent?.close, card.divergent?.divergent].filter(Boolean).length : 0
  return <section className="vibe-deck-section" aria-label="Swipe that song"><h2>SWIPE THAT SONG!</h2><p className="vibe-instructions">[ ← DISLIKE ] · SWIPE OR USE KEYS · [ LIKE (SAVE TO SWIPED) → ]</p>
    <p className="vibe-anchor">TASTE LOCK: {anchor || 'YOUR LIBRARY'} {card && <span> / {ready} CARDS BUFFERED</span>}</p>
    {error ? <div role="alert"><p>{error}</p><button className="pixel-button" onClick={() => { setError(''); setCard(null); setAttempt(value => value + 1) }}>RESTART DECK</button></div> : !card ? <p role="status">TUNING YOUR VIBE...</p> : <div className="vibe-deck" tabIndex={0} aria-label="Swipe music deck. Left arrow passes, right arrow likes." onKeyDown={event => {
      const target = event.target as HTMLElement
      if (event.repeat || target.closest('button,input,textarea,select,a') || target.isContentEditable) return
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); choose(event.key === 'ArrowRight' ? 'close' : 'divergent') }
    }}>
      <div className="vibe-card-stack">
        <div className="vibe-card-back back-two" aria-hidden="true" /><div className="vibe-card-back back-one" aria-hidden="true" />
        <article className="vibe-card pixel-panel" style={{ transform: `translateX(${Math.max(-110, Math.min(110, drag))}px) rotate(${drag / 28}deg)` }}
          onPointerDown={event => { if ((event.target as HTMLElement).closest('button') || event.button !== 0) return; origin.current = { x: event.clientX, y: event.clientY, id: event.pointerId }; event.currentTarget.setPointerCapture(event.pointerId) }}
          onPointerMove={event => { if (origin.current?.id === event.pointerId) { const dx = event.clientX - origin.current.x, dy = event.clientY - origin.current.y; if (Math.abs(dx) > Math.abs(dy)) setDrag(dx) } }}
          onPointerUp={event => { const start = origin.current; if (!start || start.id !== event.pointerId) return; const direction = swipeDirection(event.clientX - start.x, event.clientY - start.y); origin.current = null; setDrag(0); if (direction) choose(direction === 'like' ? 'close' : 'divergent') }}
          onPointerCancel={() => { origin.current = null; setDrag(0) }}>
          {drag < -15 && <span className="swipe-stamp pass-stamp">[ PASS ]</span>}{drag > 15 && <span className="swipe-stamp like-stamp">[ SWIPED! ]</span>}
          <div className="vibe-art">{card.song.thumbnailUrl && <img draggable={false} src={card.song.thumbnailUrl} alt="" onError={event => { event.currentTarget.style.visibility = 'hidden' }} />}<span aria-hidden="true">LP</span><button className="pixel-button vibe-preview" onClick={() => player.play(card.song)}>▶ PREVIEW</button></div>
          <div className="vibe-card-label"><small>MUZIC // VIBE WALK</small><h3>{card.song.title}</h3><p>{card.song.artist}</p></div>
        </article>
      </div>
      <div className="vibe-buttons"><button className="pixel-button secondary" disabled={!card.divergent} onClick={() => choose('divergent')}>[ ✖ SKIP ]</button><button className="pixel-button" disabled={!card.close} onClick={() => choose('close')}>[ ❤ SWIPE RIGHT ]</button></div>
      {(card.errors?.close || card.errors?.divergent) && <p role="status">{card.errors.close || card.errors.divergent} <button className="pixel-button secondary" onClick={() => void prefetch(card)}>REFILL</button><button className="pixel-button secondary" onClick={() => { setCard(null); setAttempt(value => value + 1) }}>RESTART DECK</button></p>}
    </div>}
    <p className="vibe-notice" aria-live="polite">{notice}</p>
  </section>
}
