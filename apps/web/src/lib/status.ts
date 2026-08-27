/** Status → visual tone mapping for badges. */

export type Tone = "gray" | "navy" | "green" | "amber" | "red" | "violet";

const TONES: Record<string, Tone> = {
  RECOVERED: "green",
  SUCCEEDED: "green",
  CAPTURED: "green",
  SENT: "green",
  FULFILLED: "green",
  APPROVED: "green",
  PASS: "green",
  ACTIVE: "green",
  PREPARING: "navy",
  EVALUATING: "violet",
  PENDING: "navy",
  SCHEDULED: "navy",
  EXECUTING: "navy",
  CREATED: "navy",
  AUTHORIZED: "navy",
  DETECTED: "navy",
  RECONCILING: "navy",
  DIAGNOSING: "violet",
  STRATEGY_SELECTED: "violet",
  POLICY_EVALUATING: "violet",
  AWAITING_APPROVAL: "amber",
  RETRYABLE_FAILURE: "amber",
  QUEUED: "amber",
  PROMISED: "amber",
  DUE: "amber",
  SIMULATED: "violet",
  FAILED: "red",
  BLOCKED: "red",
  OPTED_OUT: "red",
  EXPIRED: "red",
  CANCELLED: "red",
  REJECTED: "red",
  MISSED: "red",
  LATE_AUTHORIZED: "amber",
  REFUNDED: "gray",
  PARTIALLY_REFUNDED: "gray",
  CLOSED: "gray",
  NO_ACTION: "gray",
  UNKNOWN: "gray",
};

export function toneFor(status: string | null | undefined): Tone {
  if (!status) return "gray";
  return TONES[status.toUpperCase()] || "gray";
}

export function humanize(str: string | null | undefined): string {
  if (!str) return "—";
  return str
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
