import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

export default function PixelDialog({ children, label, className, onClose }: {
  children: ReactNode; label: string; className: string; onClose: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    const focused = document.activeElement as HTMLElement | null
    dialog?.showModal()
    return () => { dialog?.close(); focused?.focus() }
  }, [])
  return <dialog ref={ref} className={className} aria-label={label}
    onCancel={event => { event.preventDefault(); onClose() }}>{children}</dialog>
}
