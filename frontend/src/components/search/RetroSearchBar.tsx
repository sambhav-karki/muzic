import { useState } from 'react'
import type { FormEvent } from 'react'

interface Props { mode: 'ai' | 'normal'; loading: boolean; onSearch: (prompt: string) => void }
export default function RetroSearchBar({ loading, onSearch, mode }: Props) {
  const [prompt, setPrompt] = useState('')
  function submit(event: FormEvent) {
    event.preventDefault()
    if (prompt.trim() && !loading) onSearch(prompt.trim())
  }
  return <form className="retro-search pixel-panel" onSubmit={submit}>
    <label className="sr-only" htmlFor="vibe-prompt">Enter your music vibe</label>
    <span className="terminal-prompt" aria-hidden="true">&gt;</span>
    <input id="vibe-prompt" placeholder={mode === 'ai' ? 'Enter your vibe...' : 'Song title or artist...'} value={prompt} maxLength={500}
      onChange={event => setPrompt(event.target.value)} disabled={loading} required />
    <button className="pixel-button" disabled={loading || !prompt.trim()}>{loading ? 'WAIT...' : 'SEARCH'}</button>
  </form>
}
