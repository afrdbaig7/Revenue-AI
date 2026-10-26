import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, setAccessToken, setUnauthorizedHandler } from "./api";

beforeEach(() => { setAccessToken(null); document.cookie = "ra_csrf=test-csrf; path=/"; });
afterEach(() => { vi.unstubAllGlobals(); setUnauthorizedHandler(null); });

describe("browser session transport", () => {
  it("refreshes once for concurrent expired requests and retries them", async () => {
    const counts = new Map<string, number>();
    const fetcher = vi.fn(async (input: string) => {
      const count = (counts.get(input) ?? 0) + 1;
      counts.set(input, count);
      if (input.endsWith("/auth/refresh")) return new Response("{}", { status: 200 });
      return new Response(JSON.stringify({ ok: true }), { status: count === 1 ? 401 : 200 });
    });
    vi.stubGlobal("fetch", fetcher);
    expect(await Promise.all([api.get("/one"), api.get("/two")])).toEqual([{ ok: true }, { ok: true }]);
    expect(counts.get("/api/v1/auth/refresh")).toBe(1);
    expect(counts.get("/api/v1/one")).toBe(2);
  });

  it("sends the agreed CSRF header with cookie-authenticated mutations", async () => {
    const fetcher = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response("{}"));
    vi.stubGlobal("fetch", fetcher);
    await api.post("/incidents/one/cancel", { reason: "operator request" });
    expect(fetcher.mock.calls[0][1]).toMatchObject({ credentials: "include", headers: { "X-CSRF-Token": "test-csrf" } });
  });

  it("does not refresh invalid login credentials", async () => {
    const fetcher = vi.fn(async () => new Response('{"code":"UNAUTHENTICATED","message":"Invalid credentials"}', { status: 401 }));
    vi.stubGlobal("fetch", fetcher);
    await expect(api.post("/auth/login", {})).rejects.toMatchObject({ status: 401 });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("retains server support IDs and stops after one failed refresh", async () => {
    const unauthorized = vi.fn();
    setUnauthorizedHandler(unauthorized);
    const fetcher = vi.fn(async () => new Response('{"message":"Expired","correlationId":"support-123"}', { status: 401 }));
    vi.stubGlobal("fetch", fetcher);
    await expect(api.get("/incidents")).rejects.toMatchObject({ correlationId: "support-123", status: 401 });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(unauthorized).toHaveBeenCalledTimes(1);
  });
});
