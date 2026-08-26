import { defineConfig, devices } from "@playwright/test";

/**
 * RecoverAI E2E — login → dashboard → inspect incident → approve → timeline.
 *
 *   npm run e2e          (expects API on :8080 + seeded demo data; reuses running server)
 *   npx playwright install chromium   (first time)
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run start -- -p 3000",
    url: "http://localhost:3000/login",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
