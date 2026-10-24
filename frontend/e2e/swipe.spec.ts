import { test, expect } from '@playwright/test'

test('vibe deck prefetches both directions, likes immediately, and supports arrows and stamps', async ({ page }) => {
  const track = (id: number) => ({ title: `Song ${id}`, artist: `Artist ${id % 6}`, youtubeVideoId: String(id).padStart(11, '0'), thumbnailUrl: null })
  let issued = 20
  const directions: string[] = []
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    let body: unknown = { authenticated: true }
    if (url.pathname === '/api/me') body = { authenticated: true, id: 'swipe-user', name: 'Sam', pictureUrl: null }
    if (url.pathname === '/api/user/feed') body = { quickPicks: Array.from({ length: 9 }, (_, i) => track(i)), globalHits: [], forgottenFavorites: [], userPlaylists: [], primaryAnchor: '90s Rock' }
    if (url.pathname === '/api/curation/swipe-session') body = { seed: track(10), sessionId: 'vibe-a', anchorName: '90s Rock' }
    if (url.pathname === '/api/curation/next-similar') { directions.push(url.searchParams.get('distance') || ''); expect(url.searchParams.get('sessionId')).toBe('vibe-a'); body = track(issued++) }
    await route.fulfill({ contentType: 'application/json', headers: { 'access-control-allow-origin': 'http://localhost:4200', 'access-control-allow-credentials': 'true' }, body: JSON.stringify(body) })
  })
  await page.goto('/')
  const deck = page.getByRole('region', { name: 'Swipe that song' })
  const swiped = page.getByRole('button', { name: /Swiped.*saved tracks/ })
  await expect(deck.locator('h3')).toHaveText('Song 10')
  await expect(deck.locator('.vibe-anchor')).toContainText('6 CARDS BUFFERED')
  await expect(swiped).toContainText('0 saved tracks')
  const headings = await page.locator('.personal-dashboard h2').allTextContents()
  expect(headings).toEqual(['QUICK PICKS', 'FORGOTTEN FAVORITES', 'TOP GLOBAL HITS', 'SWIPE THAT SONG!', 'YOUR PLAYLISTS & SWIPED'])
  await deck.getByRole('button', { name: '[ ❤ SWIPE RIGHT ]', exact: true }).click()
  await expect(swiped).toContainText('1 saved tracks')
  await expect(deck.locator('h3')).not.toHaveText('Song 10')
  await expect(deck.locator('.vibe-anchor')).toContainText('6 CARDS BUFFERED')
  const focused = page.getByLabel('Swipe music deck. Left arrow passes, right arrow likes.')
  await focused.focus(); await focused.press('ArrowLeft')
  await expect(swiped).toContainText('1 saved tracks')
  await expect(deck.locator('.vibe-anchor')).toContainText('6 CARDS BUFFERED')
  await focused.press('ArrowRight')
  await expect(swiped).toContainText('2 saved tracks')
  await expect(deck.locator('.vibe-anchor')).toContainText('6 CARDS BUFFERED')
  const card = deck.locator('.vibe-card')
  await card.scrollIntoViewIfNeeded()
  const box = (await card.boundingBox())!
  await page.mouse.move(box.x + 90, box.y + 80); await page.mouse.down()
  await page.mouse.move(box.x + 200, box.y + 85, { steps: 5 })
  await expect(deck.locator('.like-stamp')).toBeVisible()
  await page.mouse.up()
  await expect(swiped).toContainText('3 saved tracks')
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await deck.scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'test-results/vibe-deck-mobile.png' })
  expect(directions).toContain('close'); expect(directions).toContain('divergent')
})
