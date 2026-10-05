// Used only by explicit search and Google-connect actions, never by API reads.
export function createBackendReadiness(base: string, timeoutMs = 3000, pollMs = 3500, maxWaitMs = 180000) {
  let waking = false
  type Operation = { controller: AbortController; promise: Promise<void>; callers: number }
  let pending: Operation | null = null
  const listeners = new Set<() => void>()
  const cancelled = () => new DOMException('Cancelled', 'AbortError')
  const setWaking = (value: boolean) => {
    waking = value
    listeners.forEach(listener => listener())
  }
  async function ping(signal: AbortSignal) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      // This real request to the configured Render origin starts the container.
      const response = await fetch(`${base}/api/auth/status`, {
        method: 'GET', credentials: 'include', cache: 'no-store',
        signal: AbortSignal.any([signal, controller.signal]),
      })
      if (response.status !== 200 || !response.headers.get('content-type')?.includes('application/json')) return false
      const body = await response.text()
      if (/^\s*<!doctype/i.test(body)) return false
      const status: unknown = JSON.parse(body)
      return typeof status === 'object' && status !== null && 'authenticated' in status && typeof status.authenticated === 'boolean'
    } catch { return false }
    finally { clearTimeout(timer) }
  }
  function delay(signal: AbortSignal) {
    return new Promise<void>((resolve, reject) => {
      if (signal.aborted) { reject(cancelled()); return }
      const abort = () => { clearTimeout(timer); reject(cancelled()) }
      const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, pollMs)
      signal.addEventListener('abort', abort, { once: true })
    })
  }
  async function wake(signal: AbortSignal) {
    const deadline = Date.now() + maxWaitMs
    const ready = await ping(signal)
    signal.throwIfAborted()
    if (ready) return
    setWaking(true)
    while (Date.now() < deadline) {
      await delay(signal)
      const ready = await ping(signal)
      signal.throwIfAborted()
      if (ready) return
    }
    throw new Error('Muzic is still offline. Please try again in a moment.')
  }
  function cancel() {
    const operation = pending
    pending = null
    setWaking(false)
    operation?.controller.abort(cancelled())
  }
  return {
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    getSnapshot: () => waking,
    cancel,
    ensureReady(signal?: AbortSignal): Promise<void> {
      if (signal?.aborted) return Promise.reject(cancelled())
      if (!pending) {
        const controller = new AbortController()
        const operation: Operation = { controller, promise: Promise.resolve(), callers: 0 }
        pending = operation
        operation.promise = wake(controller.signal).finally(() => {
          if (pending === operation) { pending = null; setWaking(false) }
        })
      }
      const operation = pending
      operation.callers++
      return new Promise((resolve, reject) => {
        let settled = false
        const finish = (error?: unknown) => {
          if (settled) return
          settled = true
          signal?.removeEventListener('abort', abort)
          operation.controller.signal.removeEventListener('abort', abort)
          if (--operation.callers === 0 && pending === operation) cancel()
          if (error) reject(error); else resolve()
        }
        const abort = () => finish(cancelled())
        signal?.addEventListener('abort', abort, { once: true })
        operation.controller.signal.addEventListener('abort', abort, { once: true })
        operation.promise.then(() => finish(), error => finish(error))
      })
    },
  }
}
