import { createBackendReadiness } from './backendReadiness'
export const API_BASE = (import.meta.env.VITE_API_BASE_URL || (import.meta.env.PROD ? 'https://muzic-i6fz.onrender.com' : 'http://localhost:8080')).replace(/\/+$/, '')
export const GOOGLE_LOGIN_URL = `${API_BASE}/oauth2/authorization/google`
export const backendReadiness = createBackendReadiness(API_BASE)
export function promptToSave() {
  window.dispatchEvent(new CustomEvent('muzic-toast', { detail: 'Connect your Google account to save tracks. Search and playback are free!' }))
}
export async function connectGoogle() {
  try {
    await backendReadiness.ensureReady()
    window.location.href = GOOGLE_LOGIN_URL
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') return
    window.dispatchEvent(new CustomEvent('muzic-toast', { detail: error instanceof Error ? error.message : 'Could not connect. Please try again.' }))
  }
}

export interface Song { title: string; artist: string; youtubeVideoId: string; thumbnailUrl: string | null }
export interface YouTubePlaylist { id: string; title: string; description: string; thumbnailUrl: string | null; itemCount: number }
export interface Playlist { id: string; name: string; youtubePlaylistId: string | null; songs: Song[] }
export interface Profile { authenticated: boolean; id: string | null; name: string | null; pictureUrl: string | null }
export const GUEST_PROFILE: Profile = { authenticated: false, id: null, name: null, pictureUrl: null }

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}

const unauthorizedListeners = new Set<() => void>()
export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener)
  return () => { unauthorizedListeners.delete(listener) }
}
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const abort = () => controller.abort()
  init.signal?.addEventListener('abort', abort, { once: true })
  if (init.signal?.aborted) controller.abort()
  const timer = window.setTimeout(abort, 45000)
  try {
    let response: Response
    try { response = await fetch(`${API_BASE}${path}`, { ...init, signal: controller.signal, credentials: 'include' }) }
    catch { throw new ApiError('Cannot reach Muzic or the request timed out. Please try again.', 0) }
    if (response.status === 401) {
      unauthorizedListeners.forEach(listener => listener())
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => null) as { detail?: string } | null
      throw new ApiError(problem?.detail || `Request failed (${response.status}). Please try again.`, response.status)
    }
    const body = await response.text()
    if (/^\s*</.test(body)) throw new ApiError('Muzic returned a boot page. Please try again in a moment.', 0)
    return (body ? JSON.parse(body) : undefined) as T
  } finally { window.clearTimeout(timer); init.signal?.removeEventListener('abort', abort) }
}

export const api = {
  directSearch: (query: string, signal?: AbortSignal) => request<Song[]>('/api/search/direct?query=' + encodeURIComponent(query), { signal }),
  authStatus: (signal?: AbortSignal) => request<{ authenticated: boolean }>('/api/auth/status', { signal }),
  trending: (signal?: AbortSignal) => request<Song[]>('/api/discovery/trending', { signal }),
  profile: (signal?: AbortSignal) => request<Profile>('/api/me', { signal }),
  playlists: (signal?: AbortSignal) => request<YouTubePlaylist[]>('/api/playlists', { signal }),
  playlistItems: (id: string, signal?: AbortSignal) => request<Song[]>('/api/playlists/' + encodeURIComponent(id) + '/items', { signal }),
  createPlaylist: (title: string) => request<YouTubePlaylist>('/api/playlists', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title, description: '', privacyStatus: 'private' }) }),
  addToPlaylist: (id: string, videoId: string) => request<void>('/api/playlists/' + encodeURIComponent(id) + '/items', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ videoId }) }),
  recommend: (prompt: string, signal?: AbortSignal) => request<Song[]>('/api/recommend', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ prompt }), signal,
  }),
}
