import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const songs = ['Neon Night', 'Pixel Sunrise', 'Arcade Moon'].map((title, index) => ({
  title, artist: `Artist ${index + 1}`, youtubeVideoId: String(index + 1).repeat(11), thumbnailUrl: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180"><rect width="320" height="180" fill="#172e46"/><rect x="24" y="24" width="272" height="132" fill="#287b88"/><circle cx="160" cy="90" r="50" fill="#101820"/><circle cx="160" cy="90" r="12" fill="#ffe600"/><path d="M24 130h70M226 50h70" stroke="#00e5ff" stroke-width="8"/></svg>'),
}))
const continuation = { ...songs[0], title: 'Next Vibe', youtubeVideoId: '44444444444' }

for (const selector of ['.account-banner', '.guest-likes a']) {
  test(`${selector} navigates to Google OAuth on the backend`, async ({ page }) => {
    await mock(page, false)
    const apiRequests: string[] = []
    page.on('request', request => {
      if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url())
    })
    await page.route('**/oauth2/authorization/google', async route => {
      expect(route.request().isNavigationRequest()).toBe(true)
      expect(route.request().resourceType()).toBe('document')
      await route.fulfill({ contentType: 'text/html', body: '<!doctype html><p>Google login</p>' })
    })
    await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
    await page.goto('/')
    await expect(page.locator('.trading-card h3')).toHaveText('Neon Night')
    await expect(page.locator(selector)).toHaveAttribute('href', 'http://localhost:8080/oauth2/authorization/google')
    expect(apiRequests.some(url => new URL(url).pathname === '/api/auth/status')).toBe(true)
    expect(apiRequests.some(url => new URL(url).pathname === '/api/discovery/trending')).toBe(true)
    expect(apiRequests.every(url => new URL(url).origin === 'http://localhost:8080')).toBe(true)
    await page.locator(selector).click()
    await expect(page).toHaveURL('http://localhost:8080/oauth2/authorization/google')
    await expect(page.getByText('Google login')).toBeVisible()
  })
}

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
    if (path === '/api/recommend') {
      const prompt = request.postDataJSON().prompt as string
      prompts.push(prompt)
      body = prompt.startsWith('Songs with the same vibe') || prompt.startsWith('Recommend music based') ? [continuation] : songs
    } else if (request.method() === 'POST') writes.push(path)
    await route.fulfill({ status: 200, headers, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.route('https://www.youtube.com/iframe_api', route => route.fulfill({ contentType: 'application/javascript', body: `
    window.YT = { Player: class {
      constructor(element, options) { this.options=options; this.id=''; this.iframe=document.createElement('iframe'); element.replaceWith(this.iframe); setTimeout(()=>options.events.onReady({target:this}),0); }
      getIframe(){return this.iframe} getVideoData(){return {video_id:this.id}}
      loadVideoById(id){this.id=id;this.options.events.onStateChange({target:this,data:2})}
      playVideo(){this.options.events.onStateChange({target:this,data:1})}
      pauseVideo(){this.options.events.onStateChange({target:this,data:2})}
      getCurrentTime(){return this.time || 0} getDuration(){return 180} seekTo(time){this.time=time}
      setVolume(value){this.volume=value} destroy(){this.iframe.remove()}
    }}; window.onYouTubeIframeAPIReady();
  ` }))
  return { prompts, writes }
}

test('guest search, cached intro and standalone continuation', async ({ page }) => {
  const { prompts } = await mock(page, false)
  await page.goto('/')
  await expect(page.locator('.splash-intro')).toBeVisible()
  await expect(page.locator('.splash-intro')).toHaveCount(0, { timeout: 5000 })
  await expect(page.getByText('Connect your google account to unlock more features')).toBeVisible()
  await page.getByLabel('Enter your music vibe').fill('midnight city drive')
  await page.getByRole('button', { name: 'SEARCH', exact: true }).click()
  await expect(page.locator('.recommendations .song-card')).toHaveCount(3)
  await page.getByRole('button', { name: 'Play Neon Night by Artist 1' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await expect(page.locator('.player-lcd h2')).toHaveText('Neon Night')
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.player-lcd h2')).toHaveText('Next Vibe')
  expect(prompts).toContain('Songs with the same vibe as Artist 1 - Neon Night')
  await page.getByRole('button', { name: 'Minimize player' }).click()
  await page.getByRole('button', { name: 'Stop and close player' }).click()
  await expect(page.locator('.retro-player')).toHaveCount(0)
  await page.reload()
  await expect(page.locator('.splash-intro')).toHaveCount(0)
})

test('YouTube playlists display; liked songs persist without server writes', async ({ page }) => {
  const { prompts, writes } = await mock(page, true)
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await expect(page.locator('.account-banner')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'IMPORT PLAYLIST', exact: true })).toBeVisible()
  await page.getByLabel('Enter your music vibe').fill('night drive')
  await page.getByRole('button', { name: 'SEARCH', exact: true }).click()
  await page.getByRole('button', { name: 'Play Neon Night by Artist 1' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.player-lcd h2')).toHaveText('Next Vibe')
  expect(prompts).toHaveLength(2)
  await expect(page.locator('.retro-player')).toHaveCSS('position', 'fixed')
  await page.screenshot({ path: 'test-results/muzic-player.png' })
  await page.getByRole('button', { name: 'Minimize player' }).click()
  const deck = page.getByLabel('Swipe music deck. Left arrow passes, right arrow likes.')
  await deck.focus()
  await deck.press('ArrowRight')
  await expect(page.locator('.swiped-track')).toHaveCount(1)
  await expect(page.locator('.personal-feed .song-card')).toHaveCount(1)
  await page.reload()
  await expect(page.locator('.swiped-track')).toHaveCount(1)
  expect(writes).toHaveLength(0)
  await page.screenshot({ path: 'test-results/muzic-desktop.png', fullPage: true })
})

test('mobile swipe gesture likes a track without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mock(page, true)
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  const card = page.locator('.trading-card')
  await card.scrollIntoViewIfNeeded()
  const box = await card.boundingBox()
  if (!box) throw new Error('Missing card')
  await page.mouse.move(box.x + 40, box.y + 35)
  await page.mouse.down()
  await page.mouse.move(box.x + 150, box.y + 35, { steps: 5 })
  await page.mouse.up()
  await expect(page.locator('.swiped-track')).toHaveCount(1)
  await expect(card).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await card.getByRole('button', { name: 'PREVIEW TRACK' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await expect(page.locator('.player-lcd h2')).toHaveText('Pixel Sunrise')
  const frame = await page.locator('.audio-engine iframe').boundingBox()
  expect(frame?.width).toBe(1)
  expect(frame?.x).toBeLessThan(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/muzic-mobile.png', fullPage: true })
})

test('pixel player likes, seeks, minimizes and shuffles without repeating Swiped', async ({ page }) => {
  await mock(page, true)
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await page.getByRole('button', { name: 'PREVIEW TRACK' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await expect(page.getByRole('dialog', { name: 'Music player', exact: true })).toBeVisible()
  await expect(page.locator('.audio-engine iframe')).toHaveAttribute('aria-hidden', 'true')
  await page.getByRole('button', { name: '+ ADD TO SWIPED' }).click()
  await expect(page.getByRole('button', { name: 'IN SWIPED' })).toBeDisabled()
  await expect(page.getByRole('slider', { name: 'Track position', exact: true })).toBeEnabled()
  await page.getByRole('slider', { name: 'Track position', exact: true }).fill('60')
  await expect(page.locator('.player-progress')).toContainText('1:00 / 3:00')
  await page.getByRole('button', { name: 'Minimize player' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Pause', exact: true }).click()
  await page.getByRole('button', { name: 'Play', exact: true }).click()
  await page.getByRole('button', { name: 'Shuffle', exact: true }).click()
  await expect(page.locator('.dock-title strong')).not.toHaveText('Neon Night')
  await page.getByRole('button', { name: 'Restore player from thumbnail' }).click()
  await expect(page.getByRole('dialog', { name: 'Music player', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Minimize player' }).click()
  await page.getByRole('button', { name: 'Stop and close player' }).click()
  await expect(page.locator('.swiped-library .swiped-track')).toHaveCount(1)
})

test('Swiped preview caps at three; library searches, groups and removes selected tracks', async ({ page }) => {
  await mock(page, true)
  await page.addInitScript(tracks => {
    sessionStorage.setItem('muzic:intro-seen', '1')
    if (!localStorage.getItem('muzic:swiped:v1:user-a')) localStorage.setItem('muzic:swiped:v1:user-a', JSON.stringify(tracks))
  }, [...songs, continuation])
  await page.goto('/')
  await expect(page.locator('.swiped-library > .swiped-list .swiped-track')).toHaveCount(3)
  await page.getByRole('button', { name: 'SHOW ALL' }).click()
  const library = page.getByRole('dialog', { name: 'Swiped library' })
  await expect(library.locator('.library-card')).toHaveCount(4)
  await library.getByRole('searchbox').fill('Artist 2')
  await expect(library.locator('.library-card')).toHaveCount(1)
  await library.getByRole('searchbox').fill('')
  await library.getByRole('combobox').selectOption('artist')
  await expect(library.locator('.artist-group')).toHaveCount(3)
  await library.getByRole('button', { name: 'Select Tracks' }).click()
  await library.getByRole('checkbox', { name: 'Select Neon Night', exact: true }).check()
  await library.getByRole('checkbox', { name: 'Select Pixel Sunrise', exact: true }).check()
  await library.getByRole('button', { name: 'Remove selected tracks' }).click()
  await expect(library.locator('.library-card')).toHaveCount(2)
  await page.screenshot({ path: 'test-results/muzic-library.png' })
  await library.getByRole('checkbox', { name: 'Select All', exact: true }).check()
  await library.getByRole('button', { name: 'Remove selected tracks' }).click()
  await expect(library.locator('.library-card')).toHaveCount(0)
  await page.getByRole('button', { name: 'Close Swiped library' }).click()
  await page.reload()
  await expect(page.locator('.swiped-library .swiped-track')).toHaveCount(0)
})

for (const endpoint of ['/api/auth/status', '/api/me', '/api/playlists']) {
  test(`401 from ${endpoint} returns to guest and allows Google reconnect`, async ({ page }) => {
    await mock(page, true)
    await page.route(`**${endpoint}`, route => route.fulfill({
      status: 401,
      headers: { 'access-control-allow-origin': route.request().headers().origin || 'http://localhost:4200', 'access-control-allow-credentials': 'true' },
      contentType: 'application/json', body: '{}',
    }))
    await page.route('**/oauth2/authorization/google', route => route.fulfill({
      contentType: 'text/html', body: '<p>Google login</p>',
    }))
    await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
    await page.goto('/')
    if (endpoint === '/api/playlists') await page.getByRole('button', { name: 'IMPORT PLAYLIST', exact: true }).click()
    await expect(page.locator('.account-banner')).toBeVisible()
    await expect(page.locator('.saved-playlists')).toHaveCount(0)
    await page.locator('.account-banner').click()
    await expect(page).toHaveURL(/\/oauth2\/authorization\/google$/)
  })
}
test('theme persists, space toggles playback and player fits small viewports', async ({ page }) => {
  await mock(page, true)
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await page.getByRole('button', { name: 'Toggle light theme' }).click()
  await expect(page.locator('body')).toHaveClass(/theme-light/)
  await page.reload()
  await expect(page.locator('body')).toHaveClass(/theme-light/)
  await page.getByRole('button', { name: 'PREVIEW TRACK' }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
  await page.locator('.player-lcd').click()
  await page.keyboard.press('Space')
  await expect(page.getByRole('button', { name: 'Play', exact: true })).toBeVisible()
  await page.keyboard.press('Space')
  await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
  for (const viewport of [{width:320,height:568},{width:390,height:844},{width:430,height:700},{width:844,height:390},{width:1280,height:800}]) {
    await page.setViewportSize(viewport)
    expect(await page.locator('.retro-player').evaluate(el => ({fits: el.scrollHeight <= el.clientHeight && el.scrollWidth <= el.clientWidth, height:el.clientHeight, content:el.scrollHeight})), JSON.stringify(viewport)).toMatchObject({fits:true})
    const art = await page.locator('.player-art').boundingBox()
    expect(art!.width / art!.height).toBeCloseTo(16 / 9, 1)
    await expect(page.locator('.player-actions')).toBeInViewport()
    await expect(page.locator('.player-volume')).toBeInViewport()
    if (viewport.width === 320 || viewport.width === 844) await page.screenshot({ path: 'test-results/muze-deck-' + viewport.width + '.png' })
  }
})

test('creates a YouTube playlist and adds current track to existing or new playlist', async ({ page }) => {
  await mock(page, true)
  const bodies: unknown[] = []
  await page.route('**/api/playlists**', async route => {
    if (route.request().method() === 'OPTIONS') { await route.fulfill({status:204}); return }
    const data = route.request().postDataJSON()
    if (data) bodies.push(data)
    const item = {id:'custom',title:'My playlist',description:'',thumbnailUrl:null,itemCount:0}
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(route.request().method() === 'GET' ? [item] : item)})
  })
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await page.getByRole('button', {name:'CREATE PLAYLIST',exact:true}).click()
  await page.getByLabel('Title',{exact:true}).fill('My playlist')
  await page.getByRole('button', {name:'CREATE',exact:true}).click()
  await expect(page.locator('.playlist-dialog')).toHaveCount(0)
  await page.getByRole('button', {name:'PREVIEW TRACK'}).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await page.getByRole('button', {name:'+ ADD TO PLAYLIST',exact:true}).click()
  await page.getByLabel('Playlist',{exact:true}).selectOption('custom')
  await page.getByRole('button', {name:'ADD TRACK',exact:true}).click()
  await expect(page.locator('.playlist-dialog')).toHaveCount(0)
  await page.getByRole('button', {name:'+ ADD TO PLAYLIST',exact:true}).click()
  await page.getByLabel('Title',{exact:true}).fill('Another playlist')
  await page.getByLabel('Title',{exact:true}).press('Space')
  await expect(page.getByRole('button', {name:'Pause',exact:true})).toBeVisible()
  await page.getByRole('button', {name:'ADD TRACK',exact:true}).click()
  await expect(page.locator('.playlist-dialog')).toHaveCount(0)
  expect(bodies).toContainEqual({title:'My playlist',description:'',privacyStatus:'private'})
  expect(bodies.filter(body => (body as {videoId?:string}).videoId)).toHaveLength(2)
})

test('normal search bypasses AI and track cards offer like and playlist actions', async ({ page }) => {
  const { prompts } = await mock(page, true)
  let query = ''
  let count = 3
  const inserts: string[] = []
  await page.route('**/api/search/direct?**', async route => {
    query = new URL(route.request().url()).searchParams.get('query') || ''
    await route.fulfill({contentType:'application/json',body:JSON.stringify(songs)})
  })
  await page.route('**/api/playlists**', async route => {
    if (route.request().method() === 'POST') { count++; inserts.push(route.request().postDataJSON().videoId) }
    await route.fulfill({status:route.request().method() === 'POST' ? 201 : 200, contentType:'application/json',body:JSON.stringify([{id:'music',title:'Music shelf',description:'',thumbnailUrl:null,itemCount:count}])})
  })
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await page.getByRole('button', {name:/NORMAL SEARCH/}).click()
  await expect(page.getByRole('button', {name:/NORMAL SEARCH/})).toHaveAttribute('aria-pressed','true')
  await expect(page.locator('.mode-help')).toContainText('bypasses AI')
  await page.getByLabel('Enter your music vibe').fill('song & artist')
  await page.getByRole('button', {name:'SEARCH',exact:true}).click()
  await expect(page.locator('.recommendations .song-card')).toHaveCount(3)
  expect(query).toBe('song & artist')
  expect(prompts).toHaveLength(0)
  const card = page.locator('.recommendations .track-card').first()
  await card.getByRole('button', {name:'Like Neon Night',exact:true}).click()
  await expect(card.getByRole('button', {name:'Neon Night in Swiped',exact:true})).toBeDisabled()
  await card.getByRole('button', {name:'Add Neon Night to playlist',exact:true}).click()
  await page.getByRole('button', {name:'Music shelf 3 tracks',exact:true}).click()
  await expect(page.locator('.toast')).toHaveText('Added to playlist!')
  await page.getByRole('button', { name: 'IMPORT PLAYLIST', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Music shelf 4 TRACKS', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Close import playlist' }).click()
  expect(inserts).toEqual([songs[0].youtubeVideoId])
  await page.getByRole('button', {name:/AI VIBE/}).click()
  await expect(page.locator('.mode-help')).toContainText('Describe a vibe')
  await page.getByRole('button', {name:'SEARCH',exact:true}).click()
  await expect.poll(() => prompts.length).toBe(1)
})

test('imports persist and play with a local wrapping queue', async ({ page }) => {
  const { prompts } = await mock(page, true)
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await page.getByRole('button', { name: 'IMPORT PLAYLIST', exact: true }).click()
  await page.getByRole('button', { name: /Night Drive/ }).click()
  await expect(page.getByRole('dialog', { name: 'Night Drive tracks' })).toBeVisible()
  await page.getByRole('button', { name: 'PLAY ALL', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Neon Night')
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Pixel Sunrise')
  await page.getByRole('button', { name: 'Previous track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Neon Night')
  await page.getByRole('button', { name: 'Previous track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Arcade Moon')
  expect(prompts).toHaveLength(0)
  await page.reload()
  await expect(page.locator('.imported-tag')).toHaveText('Imported / 3 TRACKS')
  await expect(page.locator('.saved-playlists a')).toHaveCount(0)
})


test('dock defaults, instant queue playback and the same iframe survive restore, minimize and background events', async ({ page }) => {
  await mock(page, true)
  await page.addInitScript(tracks => {
    sessionStorage.setItem('muzic:intro-seen', '1')
    localStorage.setItem('muzic-imported:user-a', JSON.stringify([{id:'saved-1',title:'Night Drive',songs:tracks}]))
  }, songs)
  await page.goto('/')
  await page.getByRole('button', { name: /Night Drive/ }).click()
  await page.getByRole('button', { name: 'PLAY ALL', exact: true }).click()
  await expect(page.locator('.player-dock')).toBeVisible()
  await expect(page.getByRole('dialog', { name: 'Music player', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
  const engine = page.locator('.audio-engine iframe')
  await engine.evaluate(el => el.setAttribute('data-instance', 'original'))
  await page.getByRole('button', { name: 'Pause', exact: true }).click()
  await page.getByRole('button', { name: 'Next track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Pixel Sunrise')
  await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Pause', exact: true }).click()
  await page.getByRole('button', { name: 'Previous track', exact: true }).click()
  await expect(page.locator('.dock-title strong')).toHaveText('Neon Night')
  await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Restore music player' }).click()
  await expect(page.locator('.retro-player .player-window-controls button')).toHaveCount(1)
  await expect(page.getByRole('button', { name: 'Stop and close player' })).toHaveCount(0)
  await expect(engine).toHaveAttribute('data-instance', 'original')
  await page.screenshot({ path: 'test-results/muze-deck.png' })
  await page.keyboard.press('Escape')
  await expect(page.locator('.player-dock')).toBeVisible()
  await expect(engine).toHaveAttribute('data-instance', 'original')
  await page.evaluate(() => { window.dispatchEvent(new Event('blur')); document.dispatchEvent(new Event('visibilitychange')) })
  await expect(page.getByRole('button', { name: 'Pause', exact: true })).toBeVisible()
  const frame = await engine.boundingBox()
  expect(frame?.width).toBe(1); expect(frame?.x).toBeLessThan(0)
  await page.screenshot({ path: 'test-results/muze-dock.png', fullPage: true })
  await page.getByRole('button', { name: 'Stop and close player' }).click()
  await expect(page.locator('.audio-engine iframe')).toHaveCount(0)
  await expect(page.locator('.player-dock')).toHaveCount(0)
})

test('header menu opens friends dialog and submits a real backend logout', async ({ page }) => {
  await mock(page, true)
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Your personal Muze' })).toBeVisible()
  await expect(page.locator('body')).not.toContainText('LEVEL 01')
  await expect(page.locator('body')).not.toContainText('LEVEL 02')
  await page.getByRole('button', { name: 'User menu' }).click()
  await page.getByRole('menuitem', { name: 'Add friends' }).click()
  await expect(page.getByRole('dialog', { name: 'Add friends', exact: true })).toContainText('Coming soon, Sam is working on it')
  await page.screenshot({ path: 'test-results/muze-friends.png' })
  await page.getByRole('button', { name: 'OK', exact: true }).click()
  await expect(page.getByRole('button', { name: 'User menu' })).toBeFocused()
  await page.getByRole('button', { name: 'User menu' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('menu', { name: 'Account options' })).toHaveCount(0)
  await page.route('**/logout', route => {
    expect(route.request().method()).toBe('POST')
    return route.fulfill({ contentType: 'text/html', body: '<p>Logged out</p>' })
  })
  await page.getByRole('button', { name: 'User menu' }).click()
  await page.getByRole('menuitem', { name: 'Logout' }).click()
  await expect(page).toHaveURL('http://localhost:8080/logout')
  await expect(page.getByText('Logged out')).toBeVisible()
})
