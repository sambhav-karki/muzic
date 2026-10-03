import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://localhost:4200', screenshot: 'only-on-failure' },
  webServer: { command: 'npm run dev', url: 'http://localhost:4200', reuseExistingServer: !process.env.CI },
})
