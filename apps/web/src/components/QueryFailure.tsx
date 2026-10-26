"use client";

import { ApiError } from "@/lib/api";
import { ErrorState } from "@/components/ui";

export function QueryFailure({ error, retry }: { error: Error | null; retry?: () => void }) {
  if (!error) return null;
  const status = error instanceof ApiError ? error.status : 0;
  const title = status === 403 ? "You do not have permission" : status === 404 ? "Record not found" : status === 409 ? "This record changed" : "Unable to load data";
  const supportId = error instanceof ApiError ? error.correlationId : null;
  return <ErrorState title={title} description={`${error.message || "Check your connection and try again."}${supportId ? ` Support ID: ${supportId}` : ""}`} onRetry={status === 403 || status === 404 ? undefined : retry} />;
}
