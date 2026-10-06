import { useEffect } from 'react'
import type { ReactNode } from 'react'
import './LegalPage.css'

export default function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => {
    const previousTitle = document.title
    document.title = title
    return () => { document.title = previousTitle }
  }, [title])

  return <div className="app-shell legal-shell">
    <header className="legal-header">
      <a className="brand" href="/">Muzic<span>[]</span></a>
      <a className="pixel-text legal-back" href="/">[ Back to Player ]</a>
    </header>
    <main className="legal-document pixel-panel">
      <h1>{title}</h1>
      <p className="legal-updated">Last Updated: <time dateTime="2026-10">October 2026</time></p>
      {children}
    </main>
    <footer className="site-footer pixel-text">
      <span>MUZIC BY SAM</span>
      <nav className="legal-links" aria-label="Legal">
        <a href="/terms">[ Terms of Service ]</a>
        <a href="/privacy">[ Privacy Policy ]</a>
      </nav>
    </footer>
  </div>
}
