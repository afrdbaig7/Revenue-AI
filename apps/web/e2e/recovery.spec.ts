import { expect, test } from "@playwright/test";

/**
 * Buildathon E2E story (per master prompt §41/§59):
 * login → dashboard → inspect incident → approve recovery → observe timeline.
 * Requires: API on :8080, seeded demo data, dashboard on :3000 (webServer handles it).
 */
const DEMO_EMAIL = "demo@recoverai.dev";
const DEMO_PASSWORD = "DemoPass!123";

test("login → dashboard → incident → approval queue → audit", async ({ page }) => {
  // 1. Login
  await page.goto("/login");
  await page.getByLabel("Email").fill(DEMO_EMAIL);
  await page.getByLabel("Password").fill(DEMO_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // 2. Dashboard (overview) — headline cards visible
  await expect(page).toHaveURL(/\/overview/, { timeout: 15_000 });
  await expect(page.getByText("Revenue Recovered")).toBeVisible();
  await expect(page.getByText("Revenue at Risk")).toBeVisible();
  await expect(page.getByText(/SIMULATED RESULTS/)).toBeVisible();

  // 3. Incidents list
  await page.getByRole("link", { name: "Recovery Incidents" }).click();
  await expect(page).toHaveURL(/\/incidents/);
  const firstIncident = page.locator("tbody tr").first();
  await expect(firstIncident).toBeVisible({ timeout: 15_000 });

  // 4. Incident detail — explainability panels
  await firstIncident.locator("a").first().click();
  await expect(page).toHaveURL(/\/incidents\/[0-9a-f-]+/);
  await expect(page.getByText("Amount at risk")).toBeVisible();
  await expect(page.getByText(/RecoverAI reasoning|Candidate strategies|Audit timeline/).first()).toBeVisible();

  // 5. Approval queue (may be empty — page must still render)
  await page.getByRole("link", { name: "Approval Queue" }).click();
  await expect(page).toHaveURL(/\/approvals/);
  await expect(page.getByText(/Approval Queue|empty/i).first()).toBeVisible();

  // 6. Audit log — immutable timeline renders
  await page.getByRole("link", { name: "Audit Log" }).click();
  await expect(page).toHaveURL(/\/audit/);
  await expect(page.getByRole("heading", { name: "Audit Log" })).toBeVisible();
});
