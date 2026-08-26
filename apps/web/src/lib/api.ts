/**
 * RecoverAI API client.
 *
 * Auth model: short-lived JWT access token held ONLY in memory (never localStorage),
 * delivered either via HttpOnly cookie (browser) or an explicit Bearer token from the
 * login response. Mutations carry the CSRF token when cookie-authenticated; the server
 * skips CSRF for Bearer-authenticated stateless clients.
 *
 * Transport: the browser ALWAYS talks to the same origin ("/api/v1") — the Next.js
 * server proxies API traffic to the control plane server-side (see next.config.mjs).
 * This keeps the dashboard working inside the sandboxed live preview iframe, where
 * cross-origin calls to other preview hosts may be blocked.
 */

const TOKEN_KEY = "ra_memory_token"; // module-level only — not persisted anywhere

let memoryToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  memoryToken = token;
}

export function getAccessToken() {
  return memoryToken;
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

/** Resolve the control-plane base URL. Same-origin path; proxied by Next.js. */
export function apiBase(): string {
  return "/api/v1";
}

export class ApiError extends Error {
  code: string;
  status: number;
  details: Record<string, unknown> | null;

  constructor(code: string, message: string, status: number, details: Record<string, unknown> | null) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init.headers as Record<string, string>),
  };
  const token = getAccessToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  } else if (typeof document !== "undefined") {
    // Cookie-authenticated browser flow: attach the CSRF token for mutations.
    const csrf = document.cookie
      .split("; ")
      .find((c) => c.startsWith("ra_csrf="))
      ?.split("=")[1];
    if (csrf && !["GET", "HEAD", "OPTIONS"].includes(init.method || "GET")) {
      headers["X-CSRF-Token"] = decodeURIComponent(csrf);
    }
  }

  const res = await fetch(`${apiBase()}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (res.status === 401 && onUnauthorized) {
    onUnauthorized();
  }

  if (!res.ok) {
    let body: { code?: string; message?: string; details?: Record<string, unknown> } = {};
    try {
      body = await res.json();
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(body.code || "HTTP_ERROR", body.message || res.statusText, res.status, body.details || null);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
};
