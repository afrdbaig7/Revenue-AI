/** Status → visual tone mapping for badges. */

export type Tone = "gray" | "blue" | "green" | "amber" | "red" | "violet" | "teal";

const TONES: Record<string, Tone> = {
  RECOVERED: "green",
  SUCCEEDED: "green",
  CAPTURED: "green",
  SENT: "green",
  FULFILLED: "green",
  APPROVED: "green",
  PASS: "green",
  ACTIVE: "green",
  PENDING: "blue",
  SCHEDULED: "blue",
  EXECUTING: "blue",
  CREATED: "blue",
  AUTHORIZED: "blue",
  DETECTED: "blue",
  RECONCILING: "blue",
  DIAGNOSING: "violet",
  STRATEGY_SELECTED: "violet",
  POLICY_EVALUATING: "violet",
  AWAITING_APPROVAL: "amber",
  RETRYABLE_FAILURE: "amber",
  QUEUED: "amber",
  PROMISED: "amber",
  DUE: "amber",
  SIMULATED: "teal",
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

const CLASSES: Record<Tone, string> = {
  gray: "bg-slate-100 text-slate-600 ring-slate-200",
  blue: "bg-blue-50 text-blue-700 ring-blue-200",
  green: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  amber: "bg-amber-50 text-amber-700 ring-amber-200",
  red: "bg-rose-50 text-rose-700 ring-rose-200",
  violet: "bg-violet-50 text-violet-700 ring-violet-200",
  teal: "bg-teal-50 text-teal-700 ring-teal-200",
};

export function badgeClass(status: string | null | undefined): string {
  return `inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${CLASSES[toneFor(status)]}`;
}

export function humanize(str: string | null | undefined): string {
  if (!str) return "—";
  return str
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
