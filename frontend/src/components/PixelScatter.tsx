import { useEffect, useRef, useState } from 'react'
import type { CSSProperties } from 'react'

export default function PixelScatter({ active }: { active: boolean }) {
  const [burst, setBurst] = useState(false)
  const previous = useRef(active)
  useEffect(() => {
    if (previous.current === active) return
    previous.current = active
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const start = window.requestAnimationFrame(() => setBurst(true))
    const stop = window.setTimeout(() => setBurst(false), 350)
    return () => { window.cancelAnimationFrame(start); window.clearTimeout(stop) }
  }, [active])
  return burst ? <span className="pixel-scatter" key={String(active)} aria-hidden="true">{Array.from({ length: 12 }, (_, index) => {
    const angle = index * Math.PI / 6
    return <i key={index} style={{ '--dx': `${Math.cos(angle) * (20 + index % 3 * 5)}px`, '--dy': `${Math.sin(angle) * (20 + index % 3 * 5)}px`, background: index % 2 ? '#ff007f' : '#00ffff' } as CSSProperties} />
  })}</span> : null
}
