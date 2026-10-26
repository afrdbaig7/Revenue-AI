import { expect, test, type Page } from "@playwright/test";

const incidentId = "00000000-0000-0000-0000-000000000123";
const session = (role = "OPERATOR") => ({ user: { userId: "operator-1", orgId: "org-1", email: "operator@example.test", fullName: "Test Operator", role }, orgName: "Test Merchant", permissions: role === "VIEWER" ? ["analytics:read"] : ["analytics:read", "audit:read", "recovery:manage", "recovery:approve"] });
const empty = { items: [], page: 0, size: 25, total: 0, totalPages: 0 };

async function mockSession(page: Page, role = "OPERATOR") {
  await page.route("**/api/v1/auth/me", (route) => route.fulfill({ json: session(role) }));
}

test("reload restores the nested user and organization contract", async ({ page }) => {
  await mockSession(page);
  await page.route("**/api/v1/incidents?**", (route) => route.fulfill({ json: empty }));
  await page.goto("/incidents");
  await expect(page.getByText("Test Operator").first()).toBeVisible();
  await page.reload();
  await expect(page.getByText("Test Operator").first()).toBeVisible();
  await expect(page.getByText(/Test Merchant/).first()).toBeVisible();
});

test("expired access refreshes once before retrying bootstrap", async ({ page }) => {
  let reads = 0, refreshes = 0;
  await page.route("**/api/v1/auth/me", (route) => ++reads === 1 ? route.fulfill({ status: 401, json: { message: "Expired" } }) : route.fulfill({ json: session() }));
  await page.route("**/api/v1/auth/refresh", (route) => { refreshes++; return route.fulfill({ json: {} }); });
  await page.route("**/api/v1/incidents?**", (route) => route.fulfill({ json: empty }));
  await page.goto("/incidents");
  await expect(page.getByText("Test Operator").first()).toBeVisible();
  expect(refreshes).toBe(1);
});

test("a failed incidents request is recoverable and never appears empty", async ({ page }) => {
  await mockSession(page);
  let failing = true;
  await page.route("**/api/v1/incidents?**", (route) => failing ? route.fulfill({ status: 500, json: { message: "Service unavailable", correlationId: "support-123" } }) : route.fulfill({ json: empty }));
  await page.goto("/incidents");
  await expect(page.getByRole("alert").filter({ hasText: "Unable to load data" })).toContainText("support-123");
  await expect(page.getByText("No incidents match the filters")).toHaveCount(0);
  failing = false;
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByText("No incidents match the filters")).toBeVisible();
});

test("viewer cannot see recovery mutation controls", async ({ page }) => {
  await mockSession(page, "VIEWER");
  await page.route(`**/api/v1/incidents/${incidentId}`, (route) => route.fulfill({ json: {
    incident: { id: incidentId, incidentType: "PAYMENT_FAILURE", status: "SCHEDULED", amountMinor: 10000, confirmedRecoveredMinor: 0, recoveredAmountMinor: 0, currency: "INR", createdAt: new Date().toISOString() },
    diagnoses: [], decision: null, actions: [], communications: [], attempts: [], approvals: []
  } }));
  await page.route("**/api/v1/audit-events?**", (route) => route.fulfill({ json: empty }));
  await page.goto(`/incidents/${incidentId}`);
  await expect(page.getByText("Amount at risk")).toBeVisible();
  await expect(page.getByRole("button", { name: "Cancel recovery" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Reprocess" })).toHaveCount(0);
});

test("operator approval submits a note and refreshes the queue", async ({ page }) => {
  await mockSession(page);
  let pending = true;
  await page.route("**/api/v1/auth/csrf", (route) => route.fulfill({ json: { csrfToken: "test" }, headers: { "set-cookie": "ra_csrf=test; Path=/" } }));
  await page.route("**/api/v1/approvals?**", (route) => route.fulfill({ json: { ...empty, total: pending ? 1 : 0, totalPages: pending ? 1 : 0, items: pending ? [{ id: "approval-1", incidentId, status: "PENDING", proposal: { strategy: "PAYMENT_LINK", amountMinor: 10000 } }] : [] } }));
  await page.route("**/api/v1/approvals/approval-1/approve", (route) => {
    expect(route.request().postDataJSON().note).toBe("Reviewed test proposal");
    pending = false;
    return route.fulfill({ json: { status: "APPROVED" } });
  });
  await page.goto("/approvals");
  await page.getByLabel("Decision note").fill("Reviewed test proposal");
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(page.getByText("Approval queue is empty")).toBeVisible();
});

test("public landing stays public after an unauthenticated bootstrap", async ({ page }) => {
  await page.route("**/api/v1/auth/**", (route) => route.fulfill({ status: 401, json: { message: "Sign in" } }));
  await page.goto("/");
  await expect(page).toHaveURL("http://localhost:3100/");
  await expect(page.locator("main")).toBeVisible();
});

test("date, strategy and sort filters persist across reload with IST boundaries", async ({ page }) => {
  await mockSession(page);
  let query = new URLSearchParams();
  await page.route("**/api/v1/incidents?**", (route) => { query = new URL(route.request().url()).searchParams; return route.fulfill({ json: empty }); });
  await page.goto("/incidents");
  await page.getByLabel("Detected from date").fill("2026-10-01");
  await page.getByLabel("Detected through date").fill("2026-10-02");
  await page.getByLabel("Recovery strategy").selectOption("PAYMENT_LINK");
  await page.getByLabel("Sort incidents").selectOption("amount_desc");
  await expect.poll(() => query.get("sort")).toBe("amount_desc");
  expect(query.get("createdFrom")).toBe("2026-09-30T18:30:00.000Z");
  expect(query.get("createdBefore")).toBe("2026-10-02T18:30:00.000Z");
  await page.reload();
  await expect(page.getByLabel("Recovery strategy")).toHaveValue("PAYMENT_LINK");
  await expect(page.getByLabel("Detected from date")).toHaveValue("2026-10-01");
});

async function mockIncident(page: Page) {
  await mockSession(page);
  await page.route("**/api/v1/auth/csrf", (route) => route.fulfill({ json: { csrfToken: "test" } }));
  await page.route(`**/api/v1/incidents/${incidentId}`, (route) => route.fulfill({ json: {
    incident: { id: incidentId, incidentType: "PAYMENT_FAILURE", status: "SCHEDULED", customerId: "customer-1", amountMinor: 10000, currency: "INR", createdAt: new Date().toISOString() },
    diagnoses: [], decision: null, actions: [], communications: [], attempts: [], approvals: [], promises: [],
  } }));
  await page.route("**/api/v1/audit-events?**", (route) => route.fulfill({ json: { ...empty, total: 26, totalPages: 2 } }));
}

test("operator records an explicit promise date and amount", async ({ page }) => {
  await mockIncident(page);
  let payload: Record<string, unknown> | undefined;
  await page.route(`**/api/v1/incidents/${incidentId}/promise`, (route) => { payload = route.request().postDataJSON(); return route.fulfill({ json: {} }); });
  await page.goto(`/incidents/${incidentId}`);
  await page.getByLabel("Customer commitment").fill("Customer confirmed payment tomorrow evening");
  await page.getByLabel("Confirmed date and time (IST)").fill("2030-01-02T18:30");
  await page.getByLabel("Amount (paise)", { exact: true }).fill("5000");
  await page.getByLabel("Reminder channel").selectOption("EMAIL");
  await page.getByRole("button", { name: "Record promise" }).click();
  await expect(page.getByRole("status")).toHaveText("Promise recorded.");
  expect(payload).toMatchObject({ amountMinor: 5000, channel: "EMAIL", promisedAt: "2030-01-02T13:00:00.000Z" });
});

test("cancellation requires a recorded reason and audit pages remain reachable", async ({ page }) => {
  await mockIncident(page);
  let reason = "";
  await page.route(`**/api/v1/incidents/${incidentId}/cancel`, (route) => { reason = route.request().postDataJSON().reason; return route.fulfill({ json: {} }); });
  await page.goto(`/incidents/${incidentId}`);
  await page.getByRole("button", { name: "Cancel recovery", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirm cancellation" })).toBeDisabled();
  await page.getByLabel("Cancellation reason").fill("Customer requested cancellation");
  await page.getByRole("button", { name: "Confirm cancellation" }).click();
  await expect.poll(() => reason).toBe("Customer requested cancellation");
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByText("Page 2 of 2")).toBeVisible();
});

test("policy updates require reviewing changes before submission", async ({ page }) => {
  await page.route("**/api/v1/auth/me", (route) => route.fulfill({ json: { ...session(), permissions: ["*"] } }));
  await page.route("**/api/v1/auth/csrf", (route) => route.fulfill({ json: { csrfToken: "test" } }));
  const policy = { id: "policy-1", name: "Default", active: true, version: 1, maxRetries: 3, maxContactAttempts: 3, maxDiscountPercent: 0, recoveryWindowHours: 72, minimumRecoverableAmount: 100, contactCooldownHours: 12, requireApprovalAboveAmount: 100000, allowWhatsApp: true, allowEmail: true, allowSms: false, allowDiscounts: false, allowPaymentLinks: true, allowDelayedRetry: true };
  let writes = 0;
  await page.route("**/api/v1/policies", (route) => route.fulfill({ json: [policy] }));
  await page.route("**/api/v1/policies/policy-1", (route) => { writes++; return route.fulfill({ json: { ...policy, ...route.request().postDataJSON(), version: 2 } }); });
  await page.goto("/policies");
  await page.getByLabel("Max retries per incident").fill("4");
  await page.getByRole("button", { name: "Review changes" }).click();
  await expect(page.getByText("Review policy changes (version 1)")).toBeVisible();
  expect(writes).toBe(0);
  await page.getByRole("button", { name: "Confirm policy changes" }).click();
  await expect.poll(() => writes).toBe(1);
});
