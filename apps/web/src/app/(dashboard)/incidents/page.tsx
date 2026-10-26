"use client";

import { QueryFailure } from "@/components/QueryFailure";

import { useTenantQuery as useQuery } from "@/lib/queries";
import Link from "next/link";
import { useUrlFilters } from "@/lib/url-filters";
import { api } from "@/lib/api";
import type { IncidentRow, PageResponse } from "@/lib/types";
import { formatINR, timeAgo, shortId } from "@/lib/format";
import { PageHeader, Card, StatusBadge, EmptyState, Pagination, SyntheticBanner } from "@/components/ui";

const STATUS_FILTERS = [
  "ALL",
  "DETECTED",
  "RECONCILING",
  "DIAGNOSING",
  "STRATEGY_SELECTED",
  "POLICY_EVALUATING",
  "AWAITING_APPROVAL",
  "SCHEDULED",
  "EXECUTING",
  "AWAITING_PAYMENT",
  "ESCALATED",
  "RETRYABLE_FAILURE",
  "RECOVERED",
  "BLOCKED",
  "FAILED",
  "LATE_AUTHORIZED",
  "OPTED_OUT",
  "EXPIRED",
  "CANCELLED",
  "CLOSED",
];

export default function IncidentsPage() {
  const [filters, setFilters] = useUrlFilters({ page: "0", status: "ALL", failureCategory: "", strategy: "", minAmountMinor: "", maxAmountMinor: "", from: "", through: "", sort: "newest" });
  const page = Math.max(0, Number.parseInt(filters.page, 10) || 0);
  const status = filters.status;
  const failure = filters.failureCategory;
  const setPage = (page: number) => setFilters({ page: String(page) });

  const incidents = useQuery({
    queryKey: ["incidents", filters],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: "25" });
      if (status !== "ALL") params.set("status", status);
      if (failure) params.set("failureCategory", failure);
      for (const key of ["strategy", "minAmountMinor", "maxAmountMinor"] as const) if (filters[key]) params.set(key, filters[key]);
      if (filters.from) params.set("createdFrom", new Date(`${filters.from}T00:00:00+05:30`).toISOString());
      if (filters.through) params.set("createdBefore", new Date(new Date(`${filters.through}T00:00:00+05:30`).getTime() + 86400000).toISOString());
      params.set("sort", filters.sort);
      return api.get<PageResponse<IncidentRow>>(`/incidents?${params}`);
    },
  });

  if (incidents.isError) return <QueryFailure error={incidents.error} retry={() => { void incidents.refetch(); }} />;

  const rows = incidents.data?.items ?? [];

  return (
    <div>
      <PageHeader
        title="Recovery Incidents"
        subtitle="Every unit of revenue at risk, its diagnosis, and its recovery journey."
      />
      <SyntheticBanner />

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <select
          value={status}
          aria-label="Incident status" onChange={(e) => setFilters({ status: e.target.value, page: "0" })}
          className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-100"
        >
          {STATUS_FILTERS.map((s) => (
            <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
          ))}
        </select>
        <select aria-label="Failure category" value={failure} onChange={(e) => setFilters({ failureCategory: e.target.value, page: "0" })} className="rounded-lg border px-3 py-2 text-xs">
          <option value="">All failure categories</option>
          {["INSUFFICIENT_FUNDS", "CARD_EXPIRED", "CARD_BLOCKED", "BANK_DECLINE", "MANDATE_CANCELLED", "MANDATE_FAILURE", "NETWORK_TIMEOUT", "PROCESSOR_ERROR", "AUTHENTICATION_FAILURE", "CUSTOMER_ABORTED", "CHECKOUT_ABANDONED", "UNKNOWN"].map((value) => <option key={value}>{value}</option>)}
        </select>
        <input aria-label="Minimum amount in paise" type="number" min="0" placeholder="Min amount (paise)" value={filters.minAmountMinor} onChange={(e) => setFilters({ minAmountMinor: e.target.value, page: "0" })} className="w-40 rounded-lg border px-3 py-2 text-xs" />
        <input aria-label="Maximum amount in paise" type="number" min="0" placeholder="Max amount (paise)" value={filters.maxAmountMinor} onChange={(e) => setFilters({ maxAmountMinor: e.target.value, page: "0" })} className="w-40 rounded-lg border px-3 py-2 text-xs" />
        <select aria-label="Recovery strategy" value={filters.strategy} onChange={(e) => setFilters({ strategy: e.target.value, page: "0" })} className="rounded-lg border px-3 py-2 text-xs">
          <option value="">All strategies</option>
          {["NO_ACTION", "PAYMENT_LINK", "UPI_RECOVERY", "DELAYED_RETRY", "EMAIL_NUDGE", "SMS_NUDGE", "WHATSAPP_NUDGE", "WAIT_FOR_PROVIDER_RETRY", "DISCOUNT_OFFER", "ALTERNATE_PAYMENT_METHOD", "PROMISE_TO_PAY", "MANUAL_ESCALATION"].map((value) => <option key={value}>{value}</option>)}
        </select>
        <label className="text-xs">From (IST) <input aria-label="Detected from date" type="date" value={filters.from} max={filters.through || undefined} onChange={(e) => setFilters({ from: e.target.value, page: "0" })} className="rounded-lg border px-2 py-1.5" /></label>
        <label className="text-xs">Through (IST) <input aria-label="Detected through date" type="date" value={filters.through} min={filters.from || undefined} onChange={(e) => setFilters({ through: e.target.value, page: "0" })} className="rounded-lg border px-2 py-1.5" /></label>
        <select aria-label="Sort incidents" value={filters.sort} onChange={(e) => setFilters({ sort: e.target.value, page: "0" })} className="rounded-lg border px-3 py-2 text-xs">
          <option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="amount_desc">Highest amount first</option><option value="amount_asc">Lowest amount first</option>
        </select>
        <button type="button" className="text-xs text-brand-700 underline" onClick={() => setFilters({ page: "0", status: "ALL", failureCategory: "", strategy: "", minAmountMinor: "", maxAmountMinor: "", from: "", through: "", sort: "newest" })}>Reset filters</button>
        <span className="text-xs text-slate-500">{incidents.data ? `${incidents.data.total} incidents` : "…"}</span>
      </div>

      <Card className="overflow-hidden p-0">
        {incidents.isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="skeleton h-9" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div className="p-4">
            <EmptyState title="No incidents match the filters" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table w-full">
              <thead>
                <tr>
                  <th>Incident</th>
                  <th>Amount</th>
                  <th>Failure</th>
                  <th>Status</th>
                  <th>Strategy</th>
                  <th>Confidence</th>
                  <th>Attempts</th>
                  <th>Detected</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((i) => (
                  <tr key={i.id}>
                    <td>
                      <Link href={`/incidents/${i.id}`} className="font-mono text-xs font-medium text-brand-600 hover:underline">
                        {shortId(i.id)}
                      </Link>
                      <div className="text-[10px] text-slate-400">{i.incidentType.replaceAll("_", " ")}</div>
                    </td>
                    <td className="font-semibold tabular-nums">{formatINR(i.amountMinor)}</td>
                    <td>
                      <span className="text-xs">{i.failureCategory?.replaceAll("_", " ") ?? "—"}</span>
                      <div className="text-[10px] text-slate-400">{i.diagnosisLayer ?? ""}</div>
                    </td>
                    <td><StatusBadge status={i.status} /></td>
                    <td className="text-xs">{i.selectedStrategy?.replaceAll("_", " ") ?? "—"}</td>
                    <td className="tabular-nums">{i.confidence != null ? `${(i.confidence * 100).toFixed(0)}%` : "—"}</td>
                    <td className="tabular-nums">{i.attemptsCount}</td>
                    <td className="text-xs text-slate-500">{timeAgo(i.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Pagination page={page} totalPages={incidents.data?.totalPages ?? 0} onChange={setPage} />
    </div>
  );
}
