import { NextRequest } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const target = new URL(process.env.API_PROXY_TARGET || "http://localhost:8080");
  const destination = new URL(`/api/v1/${path.map(encodeURIComponent).join("/")}`, target);
  destination.search = request.nextUrl.search;
  const headers = new Headers(request.headers);
  for (const header of ["host", "connection", "content-length", "transfer-encoding", "x-forwarded-host", "x-forwarded-for"]) headers.delete(header);
  let body: ArrayBuffer | undefined;
  if (!["GET", "HEAD"].includes(request.method)) {
    const chunks: Uint8Array[] = [];
    let length = 0;
    const reader = request.body?.getReader();
    if (reader) {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        length += value.byteLength;
        if (length > 1_048_576) { await reader.cancel(); return Response.json({ code: "VALIDATION_ERROR", message: "Request too large" }, { status: 413 }); }
        chunks.push(value);
      }
      const data = new Uint8Array(length);
      let offset = 0;
      for (const chunk of chunks) { data.set(chunk, offset); offset += chunk.byteLength; }
      body = data.buffer;
    }
  }
  try {
    const upstream = await fetch(destination, { method: request.method, headers, body, redirect: "manual", cache: "no-store", signal: AbortSignal.any([request.signal, AbortSignal.timeout(30_000)]) });
    const responseHeaders = new Headers(upstream.headers);
    for (const header of ["content-encoding", "content-length", "connection", "transfer-encoding"]) responseHeaders.delete(header);
    responseHeaders.set("Cache-Control", "no-store");
    responseHeaders.delete("set-cookie");
    for (const cookie of upstream.headers.getSetCookie()) responseHeaders.append("set-cookie", cookie);
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return Response.json({ code: "UPSTREAM_UNAVAILABLE", message: "The service is temporarily unavailable. Try again shortly." }, { status: 502 });
  }
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE, proxy as HEAD, proxy as OPTIONS };
