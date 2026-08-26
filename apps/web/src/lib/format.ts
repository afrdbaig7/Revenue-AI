/** Formatting helpers: money in integer minor units (INR), dates, compact numbers. */

export function formatINR(minor: number, opts: { compact?: boolean; noDecimals?: boolean } = {}): string {
  const rupees = minor / 100;
  const formatter = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: opts.noDecimals ? 0 : 2,
    minimumFractionDigits: opts.noDecimals ? 0 : rupees % 1 === 0 ? 0 : 2,
    notation: opts.compact ? "compact" : "standard",
  });
  return formatter.format(rupees);
}

export function formatNumber(n: number): string {
  return new Intl.NumberFormat("en-IN").format(n);
}

export function formatPercent(p: number, digits = 1): string {
  return `${p.toFixed(digits)}%`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return "—";
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}

export function shortId(uuid: string | null | undefined): string {
  if (!uuid) return "—";
  return uuid.slice(0, 8);
}
