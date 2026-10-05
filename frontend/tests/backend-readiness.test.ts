import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createBackendReadiness } from '../src/services/backendReadiness.ts'

const healthy = () => new Response('{"authenticated":false}', { headers: { 'content-type': 'application/json' } })

test('HTML boot pages and network failures poll once for concurrent callers, then resume', async () => {
  const original = globalThis.fetch
  let calls = 0
  globalThis.fetch = async () => {
    calls++
    if (calls === 1) return new Response('<!doctype html>Render booting', { headers: { 'content-type': 'application/json' } })
    if (calls === 2) throw new Error('offline')
    return healthy()
  }
  try {
    const backend = createBackendReadiness('http://test', 20, 1, 1000)
    const states: boolean[] = []
    const unsubscribe = backend.subscribe(() => states.push(backend.getSnapshot()))
    await Promise.all([backend.ensureReady(), backend.ensureReady()])
    assert.equal(calls, 3)
    assert.deepEqual(states, [true, false])
    unsubscribe()
  } finally { globalThis.fetch = original }
})

test('timed-out health checks wake and retry; cancelled callers never resume', async () => {
  const original = globalThis.fetch
  let calls = 0
  globalThis.fetch = async (_input, init) => {
    if (++calls > 1) return healthy()
    return new Promise((_resolve, reject) => init?.signal?.addEventListener('abort', () => reject(new Error('timeout')), { once: true }))
  }
  try {
    const backend = createBackendReadiness('http://test', 5, 1, 1000)
    const controller = new AbortController()
    const cancelled = backend.ensureReady(controller.signal)
    const active = backend.ensureReady()
    controller.abort()
    await assert.rejects(cancelled, { name: 'AbortError' })
    await active
    assert.equal(calls, 2)
    assert.equal(backend.getSnapshot(), false)
  } finally { globalThis.fetch = original }
})

test('an extended outage ends with a retryable error and clears waking state', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response('{}', { status: 503 })
  try {
    const backend = createBackendReadiness('http://test', 5, 1, 5)
    await assert.rejects(backend.ensureReady(), /still offline/)
    assert.equal(backend.getSnapshot(), false)
    globalThis.fetch = async () => healthy()
    await backend.ensureReady()
  } finally { globalThis.fetch = original }
})

test('only a valid 200 JSON status resumes an action', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response('{}', { status: 401, headers: { 'content-type': 'application/json' } })
  try {
    const backend = createBackendReadiness('http://test', 5, 1, 10)
    await assert.rejects(backend.ensureReady(), /still offline/)
    assert.equal(backend.getSnapshot(), false)
  } finally { globalThis.fetch = original }
})

test('dismiss cancels pending callers and stops probes; a new action can retry', async () => {
  const original = globalThis.fetch
  let calls = 0
  globalThis.fetch = async () => { calls++; throw new Error('offline') }
  try {
    const backend = createBackendReadiness('https://muzic-i6fz.onrender.com', 5, 5, 1000)
    const first = backend.ensureReady()
    const second = backend.ensureReady()
    const rejected = Promise.all([assert.rejects(first, { name: 'AbortError' }), assert.rejects(second, { name: 'AbortError' })])
    await new Promise(resolve => setTimeout(resolve, 1))
    assert.equal(backend.getSnapshot(), true)
    backend.cancel()
    await rejected
    await new Promise(resolve => setTimeout(resolve, 15))
    assert.equal(calls, 1)
    assert.equal(backend.getSnapshot(), false)
    globalThis.fetch = async (url, init) => {
      assert.equal(url, 'https://muzic-i6fz.onrender.com/api/auth/status')
      assert.equal(init?.method, 'GET')
      assert.equal(init?.credentials, 'include')
      return healthy()
    }
    await backend.ensureReady()
  } finally { globalThis.fetch = original }
})
