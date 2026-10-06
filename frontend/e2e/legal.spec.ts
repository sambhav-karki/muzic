import { test, expect } from '@playwright/test'

for (const [path, title] of [['/privacy', 'Muzic - Privacy Policy'], ['/terms', 'Muzic - Terms of Service']]) {
  test(`${path} is public without backend or player initialization`, async ({ page }) => {
    const backendRequests: string[] = []
    page.on('request', request => {
      const url = new URL(request.url())
      if (url.port === '8080' || url.hostname.endsWith('onrender.com') || url.pathname.startsWith('/api/') || /health|oauth2/.test(url.pathname)) backendRequests.push(request.url())
    })
    await page.goto(path)
    await expect(page).toHaveTitle(title)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(title)
    await expect(page.getByText('Last Updated: October 2026')).toBeVisible()
    await expect(page.locator('.legal-security-notice')).toContainText(/SSL/)
    await expect(page.getByRole('link', { name: '[ Back to Player ]' })).toHaveAttribute('href', '/')
    await expect(page.locator('.app-shell .hero-deck, .wake-terminal, .retro-player')).toHaveCount(0)
    await page.reload()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(title)
    await page.getByRole('link', { name: path === '/privacy' ? '[ Terms of Service ]' : '[ Privacy Policy ]' }).click()
    await expect(page).toHaveURL(path === '/privacy' ? '/terms' : '/privacy')
    expect(backendRequests).toEqual([])
  })
}

test('landing footer links open legal documents', async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({ json: { authenticated: false } }))
  await page.addInitScript(() => sessionStorage.setItem('muzic:intro-seen', '1'))
  await page.goto('/')
  const footer = page.getByRole('contentinfo')
  await expect(footer.getByRole('link', { name: '[ Terms of Service ]' })).toHaveAttribute('href', '/terms')
  await footer.getByRole('link', { name: '[ Privacy Policy ]' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Muzic - Privacy Policy')
  await page.getByRole('link', { name: '[ Back to Player ]' }).click()
  await expect(page).toHaveURL('/')
  await footer.getByRole('link', { name: '[ Terms of Service ]' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Muzic - Terms of Service')
})
