import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const songs = ['Neon Night', 'Pixel Sunrise', 'Arcade Moon'].map((title, index) => ({
  title, artist: `Artist ${index + 1}`, youtubeVideoId: String(index + 1).repeat(11), thumbnailUrl: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180"><rect width="320" height="180" fill="#172e46"/><rect x="24" y="24" width="272" height="132" fill="#287b88"/><circle cx="160" cy="90" r="50" fill="#101820"/><circle cx="160" cy="90" r="12" fill="#ffe600"/><path d="M24 130h70M226 50h70" stroke="#00e5ff" stroke-width="8"/></svg>'),
}))
const continuation = { ...songs[0], title: 'Next Vibe', youtubeVideoId: '44444444444' }

async function mock(page: Page, authenticated: boolean) {
  const prompts: string[] = []
  const writes: string[] = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const headers = { 'access-control-allow-origin': route.request().headers().origin || 'http://localhost:4200', 'access-control-allow-credentials': 'true', 'access-control-allow-headers': 'Content-Type', 'access-control-allow-methods': 'GET,POST,OPTIONS' }
    if (request.method() === 'OPTIONS') { await route.fulfill({ status: 204, headers }); return }
    let body: unknown = []
    if (path === '/api/auth/status') body = { authenticated }
    if (path === '/api/me') body = { authenticated, id: authenticated ? 'user-a' : null, name: 'Sam', pictureUrl: null }
    if (path === '/api/playlists') body = [{ id: 'saved-1', title: 'Night Drive', description: '', thumbnailUrl: null, itemCount: 3 }]
    if (path === '/api/playlists/saved-1/items') body = songs
    if (path === '/api/discovery/trending') body = songs
    if (path === '/api/search') body = { tracks: songs, totalPages: 1, nextPageToken: '' }
    if (path === '/api/user/feed') body = { quickPicks: songs, forgottenFavorites: songs, globalHits: songs, primaryAnchor: 'Night Drive', userPlaylists: [{ id: 'saved-1', title: 'Night Drive', itemCount: 3 }] }
    if (path === '/api/curation/next-similar') body = continuation
    if (path === '/api/recommend') {
      const prompt = request.postDataJSON().prompt as string
      prompts.push(prompt)
      body = prompt.startsWith('Songs with the same vibe') || prompt.startsWith('Recommend music based') ? [continuation] : songs
    } else if (request.method() === 'POST') writes.push(path)
    await route.fulfill({ status: 200, headers, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.route('https://www.youtube.com/iframe_api', route => route.fulfill({ contentType: 'application/javascript', body: `
    window.YT = { Player: class {
      constructor(element, options) { this.options=options; this.id=''; this.iframe=document.createElement('iframe'); element.replaceWith(this.iframe); window.addEventListener('test-yt-error', event=>options.events.onError({target:this,data:event.detail})); window.addEventListener('test-yt-ended', ()=>options.events.onStateChange({target:this,data:0})); setTimeout(()=>options.events.onReady({target:this}),0); }
      getIframe(){return this.iframe} getVideoData(){return {video_id:this.id}}
      loadVideoById(id){this.id=id;this.iframe.setAttribute('data-video-id',id);this.options.events.onStateChange({target:this,data:2})}
      playVideo(){this.options.events.onStateChange({target:this,data:1})}
      pauseVideo(){this.options.events.onStateChange({target:this,data:2})}
      getCurrentTime(){return this.time || 0} getDuration(){return 180} seekTo(time){this.time=time}
      setVolume(value){this.volume=value} destroy(){this.iframe.remove()}
    }}; window.onYouTubeIframeAPIReady();
  ` }))
  return { prompts, writes }
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
})

test('guest search starts playback, next curates, artwork restores and Space toggles', async ({ page }) => {
  await mock(page, false)
  await page.goto('/')
  await page.getByLabel('Enter your music vibe').fill('night drive')
  await page.getByRole('button', { name: 'SEARCH', exact: true }).click()
  await page.getByRole('button', { name: 'Play Neon Night by Artist 1' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Next Vibe')
  await page.getByRole('button', { name: 'Restore player from thumbnail' }).click()
  await expect(page.locator('.player-lcd h2')).toHaveText('Next Vibe')
  await page.getByRole('slider', { name: 'Track position', exact: true }).fill('60')
  await expect(page.locator('.player-progress')).toContainText('1:00 / 3:00')
  await page.getByRole('button', { name: 'Minimize player' }).click()
  await page.locator('body').click({ position: { x: 5, y: 5 } })
  await page.keyboard.press('Space')
  await expect(page.getByRole('button', { name: 'Play', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Stop and close player' }).click()
  await expect(page.locator('.audio-engine iframe')).toHaveCount(0)
})

test('remote playlist plays a wrapping queue and retains its iframe across expansion', async ({ page }) => {
  const { prompts } = await mock(page, true)
  await page.goto('/')
  await page.getByRole('button', { name: /Night Drive/ }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Neon Night')
  const engine = page.locator('.audio-engine iframe')
  await engine.evaluate(el => el.setAttribute('data-instance', 'original'))
  await page.getByRole('button', { name: 'Previous track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Arcade Moon')
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Neon Night')
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await expect(engine).toHaveAttribute('data-instance', 'original')
  await page.keyboard.press('Escape')
  await expect(engine).toHaveAttribute('data-instance', 'original')
  expect(prompts).toHaveLength(0)
})

test('shelf likes persist and theme survives reload', async ({ page }) => {
  const { writes } = await mock(page, true)
  await page.goto('/')
  await page.locator('.quick-picks').getByRole('button', { name: 'Like Neon Night', exact: true }).click()
  await page.getByRole('button', { name: 'Switch to light theme' }).click()
  await page.reload()
  await expect(page.locator('body')).toHaveClass(/theme-light/)
  await expect(page.locator('.quick-picks').getByRole('button', { name: 'Neon Night in Swiped', exact: true })).toBeDisabled()
  expect(writes).toHaveLength(0)
})

test('personal connect navigates to backend OAuth and logout uses POST', async ({ page }) => {
  await mock(page, false)
  await page.route('**/oauth2/authorization/google', route => route.fulfill({ contentType: 'text/html', body: '<p>Google login</p>' }))
  await page.goto('/')
  await page.getByRole('button', { name: '[ > YOUR PERSONAL MUZE < ]' }).click()
  await expect(page).toHaveURL('http://localhost:8080/oauth2/authorization/google')
  await mock(page, true)
  await page.goto('http://localhost:4200/')
  await page.route('**/logout', route => {
    expect(route.request().method()).toBe('POST')
    return route.fulfill({ contentType: 'text/html', body: '<p>Logged out</p>' })
  })
  await page.getByRole('button', { name: 'LOG OUT', exact: true }).click()
  await expect(page.getByText('Logged out')).toBeVisible()
})

test('sleeping backend does not wake on guest landing', async ({ page }) => {
  await page.clock.install()
  await mock(page, false)
  let probes = 0
  await page.route('**/api/**', route => {
    if (new URL(route.request().url()).pathname === '/api/auth/status') probes++
    return route.fulfill({ contentType: 'text/html', body: '<p>Starting</p>' })
  })
  await page.goto('/')
  await expect(page.getByLabel('Enter your music vibe')).toBeEnabled()
  await page.clock.fastForward(10000)
  await expect(page.locator('.wake-terminal')).toHaveCount(0)
  expect(probes).toBe(0)
})

for (const code of [2, 100, 101, 150]) {
  test(`YouTube error ${code} automatically advances once`, async ({ page }) => {
    await mock(page, true)
    let requests = 0
    await page.route('**/api/curation/next-similar?**', async route => { requests++; await route.fulfill({ contentType: 'application/json', body: JSON.stringify(continuation) }) })
    await page.goto('/')
    await page.locator('.quick-picks').getByRole('button', { name: 'Play Neon Night by Artist 1' }).click()
    await expect(page.locator('.audio-engine iframe')).toHaveAttribute('data-video-id', songs[0].youtubeVideoId)
    await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
    await page.evaluate(code => {
      window.dispatchEvent(new CustomEvent('test-yt-error', { detail: code }))
      window.dispatchEvent(new CustomEvent('test-yt-error', { detail: code }))
    }, code)
    await expect(page.locator('.dock-title strong')).toHaveText('Next Vibe')
    await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
    expect(requests).toBe(1)
  })
}

test('mobile dock has large primary controls and a 48px cover', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mock(page, true)
  await page.goto('/')
  await page.locator('.quick-picks').getByRole('button', { name: 'Play Neon Night by Artist 1' }).click()
  await expect(page.locator('.player-dock')).toHaveCSS('height', '70px')
  await expect(page.locator('.dock-art')).toHaveCSS('width', '48px')
  await expect(page.getByRole('button', { name: 'Previous track' })).toBeHidden()
  await expect(page.getByRole('button', { name: 'Shuffle', exact: true })).toBeHidden()
  expect((await page.getByRole('button', { name: 'Pause', exact: true }).boundingBox())!.height).toBeGreaterThanOrEqual(44)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/mobile-dashboard-dock.png', fullPage: true })
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect(page.getByRole('button', { name: 'Search music' })).toBeInViewport()
  await expect(page.getByRole('button', { name: 'LOG OUT', exact: true })).toBeInViewport()
  await page.getByRole('button', { name: 'Search music' }).click()
  await expect(page.getByLabel('Enter your music vibe')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('a restricted playlist track is skipped on later queue wraps', async ({ page }) => {
  await mock(page, true)
  await page.goto('/')
  await page.getByRole('button', { name: /Night Drive/ }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Neon Night')
  await expect(page.locator('.audio-engine iframe')).toHaveAttribute('data-video-id', songs[0].youtubeVideoId)
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('test-yt-error', { detail: 150 })))
  await expect(page.locator('.dock-title strong')).toHaveText('Pixel Sunrise')
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Arcade Moon')
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Pixel Sunrise')
})
