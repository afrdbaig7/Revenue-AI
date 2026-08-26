"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
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
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("ALL");
  const [failure, setFailure] = useState("");

  const incidents = useQuery({
    queryKey: ["incidents", page, status, failure],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: "25" });
      if (status !== "ALL") params.set("status", status);
      if (failure) params.set("failureCategory", failure);
      return api.get<PageResponse<IncidentRow>>(`/incidents?${params}`);
    },
  });

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
          onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-100"
        >
          {STATUS_FILTERS.map((s) => (
            <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
          ))}
        </select>
        <input
          value={failure}
          onChange={(e) => { setFailure(e.target.value); setPage(0); }}
          placeholder="Filter by failure category…"
          className="w-56 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-100"
        />
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
