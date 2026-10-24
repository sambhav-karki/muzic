import { test, expect } from '@playwright/test'

const songs = Array.from({ length: 9 }, (_, index) => ({ title: `Track ${index + 1}`, artist: `Test artist ${index}`, youtubeVideoId: String(index + 1).repeat(11), thumbnailUrl: null }))
for (const authenticated of [false, true]) {
  test(`${authenticated ? 'personal dashboard' : 'guest hub pagination'} renders the correct stage`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
    let feedRequests = 0
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url())
      let body: unknown = { authenticated: true }
      if (url.pathname === '/api/me') body = { authenticated, id: authenticated ? 'user-a' : null, name: 'Sam', pictureUrl: null }
      if (url.pathname === '/api/user/feed') body = { quickPicks: ++feedRequests % 2 === 0 ? songs.slice().reverse() : songs, forgottenFavorites: songs.map(song => ({ ...song, youtubeVideoId: 'f' + song.youtubeVideoId.slice(1) })), globalHits: songs, primaryAnchor: 'Night Drive', userPlaylists: [{ id: 'list', title: 'Night Drive', itemCount: 9 }] }
      if (url.pathname === '/api/search') body = { tracks: url.searchParams.get('page') === '1' ? songs.map(song => ({ ...song, title: song.title + ' page two' })) : songs, totalPages: 2, nextPageToken: url.searchParams.get('page') === '1' ? '' : 'next-token' }
      await route.fulfill({ contentType: 'application/json', headers: { 'access-control-allow-origin': 'http://localhost:4200', 'access-control-allow-credentials': 'true' }, body: JSON.stringify(body) })
    })
    await page.goto('/')
    if (authenticated) {
      await expect(page.getByText('Welcome to your frequency.')).toBeVisible()
      await expect(page.locator('.music-shelf')).toHaveCount(3)
      await expect(page.locator('.quick-picks-grid .track-card')).toHaveCount(9)
      await expect(page.getByText('Night Drive', { exact: true })).toBeVisible()
      await expect(page.locator('.quick-heading')).toContainText('Primary Anchor: Night Drive')
      const pinnedTitle = await page.locator('.quick-slot[data-slot="1"] h3').innerText()
      await page.locator('.quick-slot[data-slot="1"]').getByRole('button', { name: 'Pin ' + pinnedTitle, exact: true }).click()
      const previousRequests = feedRequests
      await page.getByRole('button', { name: 'REFRESH PICKS' }).click()
      await expect.poll(() => feedRequests).toBeGreaterThan(previousRequests)
      await expect(page.locator('.quick-slot[data-slot="1"]')).toHaveClass(/pinned/)
      await expect(page.locator('.quick-slot[data-slot="1"] h3')).toHaveText(pinnedTitle)
      await page.reload()
      await expect(page.locator('.quick-slot[data-slot="1"] h3')).toHaveText(pinnedTitle)
      await expect(page.getByRole('button', { name: 'Unpin ' + pinnedTitle, exact: true })).toHaveAttribute('aria-pressed', 'true')
      await page.screenshot({ path: 'test-results/personal-pinned-grid.png', fullPage: true })
      await page.getByRole('button', { name: 'Search music' }).click()
      await expect(page.getByLabel('Enter your music vibe')).toBeVisible()
      await page.getByRole('button', { name: 'Search music' }).click()
      await expect(page.getByLabel('Enter your music vibe')).toHaveCount(0)
      await page.locator('.music-shelf').first().getByRole('button', { name: 'Next FORGOTTEN FAVORITES' }).click()
      await expect.poll(() => page.locator('.shelf-track').first().evaluate(element => element.scrollLeft)).toBeGreaterThan(0)
    } else {
      await expect(page.getByRole('heading', { name: 'Muze away' })).toBeVisible()
      await expect(page.locator('.recommendations')).toHaveCount(0)
      await expect(page.getByText('Try "midnight city drive" or "sunny afternoon jazz".')).toHaveCount(0)
      await page.screenshot({ path: 'test-results/guest-centered.png' })
      await expect(page.getByRole('button', { name: '[ > YOUR PERSONAL MUZE < ]' })).toBeVisible()
      await page.getByRole('button', { name: 'NORMAL SEARCH', exact: true }).click()
      await page.getByLabel('Enter your music vibe').fill('jazz')
      await page.getByRole('button', { name: 'SEARCH', exact: true }).click()
      await expect(page.locator('.track-card')).toHaveCount(9)
      await expect(page.getByText('PAGE 1 OF 2')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(true)
      await page.getByRole('button', { name: '[ NEXT > ]' }).click()
      await expect(page.getByText('PAGE 2 OF 2')).toBeVisible()
      await expect(page.getByRole('button', { name: '[ NEXT > ]' })).toBeDisabled()
      await page.getByRole('button', { name: '[ < PREV ]' }).click()
      await expect(page.getByText('PAGE 1 OF 2')).toBeVisible()
      await page.screenshot({ path: 'test-results/guest-hub.png' })
      await page.getByRole('button', { name: 'Play Track 1 by Test artist 0' }).click()
      await expect(page.locator('.guest-active')).toHaveCount(1)
      await expect(page.locator('.hero-tagline')).toBeHidden()
      await expect(page.locator('.mode-help')).toHaveCount(0)
      await expect(page.locator('.muze-badge')).toBeVisible()
      await expect(page.locator('.search-modes button').first()).toHaveCSS('height', '36px')
      for (const card of await page.locator('.song-grid .track-card').all()) {
        expect((await card.boundingBox())!.height).toBeGreaterThanOrEqual(52)
      }
      await expect(page.locator('.song-grid .track-card').last()).toBeInViewport()
      await page.screenshot({ path: 'test-results/guest-playing-grid.png' })
      await page.getByRole('button', { name: 'Pause', exact: true }).click()
      await expect(page.locator('.guest-active')).toHaveCount(1)
      await page.getByRole('button', { name: 'Stop and close player' }).click()
      await expect(page.locator('.guest-active')).toHaveCount(0)
      await expect(page.locator('.hero-tagline')).toBeVisible()
      for (const viewport of [{ width: 1366, height: 600 }, { width: 390, height: 844 }]) {
        await page.setViewportSize(viewport)
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        if (viewport.width > 800) {
          expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(true)
          await expect(page.locator('.track-card').last()).toBeInViewport()
          await expect(page.getByRole('button', { name: '[ NEXT > ]' })).toBeInViewport()
        }
        if (viewport.width < 800) {
          await expect(page.getByRole('button', { name: 'NORMAL SEARCH', exact: true })).toHaveCSS('min-height', '44px')
          await page.screenshot({ path: 'test-results/mobile-guest.png', fullPage: true })
        }
      }
    }
  })
}
