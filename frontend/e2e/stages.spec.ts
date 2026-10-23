import { test, expect } from '@playwright/test'

const songs = Array.from({ length: 9 }, (_, index) => ({ title: `Track ${index + 1}`, artist: 'Test artist', youtubeVideoId: String(index + 1).repeat(11), thumbnailUrl: null }))
for (const authenticated of [false, true]) {
  test(`${authenticated ? 'personal dashboard' : 'guest hub pagination'} renders the correct stage`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url())
      let body: unknown = { authenticated: true }
      if (url.pathname === '/api/me') body = { authenticated, id: authenticated ? 'user-a' : null, name: 'Sam', pictureUrl: null }
      if (url.pathname === '/api/user/feed') body = { quickPicks: songs, trending: songs, userPlaylists: [{ id: 'list', title: 'Night Drive', itemCount: 9 }] }
      if (url.pathname === '/api/search') body = { tracks: url.searchParams.get('page') === '1' ? songs.map(song => ({ ...song, title: song.title + ' page two' })) : songs, totalPages: 2, nextPageToken: url.searchParams.get('page') === '1' ? '' : 'next-token' }
      await route.fulfill({ contentType: 'application/json', headers: { 'access-control-allow-origin': 'http://localhost:4200', 'access-control-allow-credentials': 'true' }, body: JSON.stringify(body) })
    })
    await page.goto('/')
    if (authenticated) {
      await expect(page.getByText('Welcome to your frequency.')).toBeVisible()
      await expect(page.locator('.music-shelf')).toHaveCount(3)
      for (const shelf of await page.locator('.shelf-track').all()) {
        expect(await shelf.locator(':scope > *').count()).toBeGreaterThanOrEqual(9)
      }
      await expect(page.getByText('Night Drive')).toBeVisible()
      await page.getByRole('button', { name: 'Search music' }).click()
      await expect(page.getByLabel('Enter your music vibe')).toBeVisible()
      await page.getByRole('button', { name: 'Search music' }).click()
      await expect(page.getByLabel('Enter your music vibe')).toHaveCount(0)
      await page.locator('.music-shelf').first().getByRole('button', { name: 'Next QUICK PICKS / MIXES' }).click()
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
      for (const viewport of [{ width: 1366, height: 600 }, { width: 390, height: 844 }]) {
        await page.setViewportSize(viewport)
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        if (viewport.width > 800) {
          expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(true)
          await expect(page.locator('.track-card').last()).toBeInViewport()
          await expect(page.getByRole('button', { name: '[ NEXT > ]' })).toBeInViewport()
        }
        if (viewport.width < 800) {
          expect((await page.getByRole('button', { name: 'NORMAL SEARCH', exact: true }).boundingBox())!.height).toBeGreaterThanOrEqual(44)
          await page.screenshot({ path: 'test-results/mobile-guest.png', fullPage: true })
        }
      }
    }
  })
}
