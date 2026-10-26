"use client";

import { QueryFailure } from "@/components/QueryFailure";

import { useTenantQuery as useQuery } from "@/lib/queries";
import { useUrlFilters } from "@/lib/url-filters";
import Link from "next/link";
import { api } from "@/lib/api";
import type { AuditEvent, PageResponse } from "@/lib/types";
import { formatDateTime, shortId } from "@/lib/format";
import { PageHeader, Card, StatusBadge, EmptyState, Pagination, Skeleton } from "@/components/ui";

export default function AuditPage() {
  const [filters, setFilters] = useUrlFilters({ page: "0", eventType: "" });
  const page = Math.max(0, Number.parseInt(filters.page, 10) || 0);
  const eventType = filters.eventType;
  const setPage = (value: number) => setFilters({ page: String(value) });

  const audit = useQuery({
    queryKey: ["audit-all", page, eventType],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: "50" });
      if (eventType) params.set("eventType", eventType);
      return api.get<PageResponse<AuditEvent>>(`/audit-events?${params}`);
    },
  });

  if (audit.isError) return <QueryFailure error={audit.error} retry={() => { void audit.refetch(); }} />;

  const rows = audit.data?.items ?? [];

  return (
    <div>
      <PageHeader
        title="Audit Log"
        subtitle="Immutable record of every detection, decision, policy check, action, and outcome. Read-only."
      />
      <input
        value={eventType}
        onChange={(e) => setFilters({ eventType: e.target.value, page: "0" })}
        aria-label="Audit event type" placeholder="Filter by event type (e.g. POLICY_BLOCKED)…"
        className="mb-3 w-72 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-100"
      />

      <Card className="overflow-hidden p-0">
        {audit.isLoading ? (
          <div className="space-y-2 p-4">{Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} className="h-8" />)}</div>
        ) : rows.length === 0 ? (
          <div className="p-4"><EmptyState title="No audit events match" /></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table w-full">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Event</th>
                  <th>Entity</th>
                  <th>Transition</th>
                  <th>Actor</th>
                  <th>Incident</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.id}>
                    <td className="whitespace-nowrap text-xs text-slate-500">{formatDateTime(e.timestamp)}</td>
                    <td className="font-medium text-slate-800">{e.eventType.replaceAll("_", " ")}
                      {e.decisionInputSnapshot || e.decisionOutputSnapshot ? <details className="mt-1 text-xs"><summary className="cursor-pointer">View recorded changes</summary><pre className="mt-2 max-h-64 max-w-lg overflow-auto rounded bg-slate-50 p-2 text-[10px]">{JSON.stringify({ before: e.decisionInputSnapshot, after: e.decisionOutputSnapshot }, null, 2)}</pre></details> : null}
                    </td>
                    <td className="text-xs">
                      {e.entityType}
                      <span className="ml-1 font-mono text-[10px] text-slate-400">{shortId(e.entityId)}</span>
                    </td>
                    <td>
                      <div className="flex items-center gap-1 text-xs">
                        {e.previousState ? <StatusBadge status={e.previousState} /> : null}
                        {e.previousState && e.newState ? <span className="text-slate-400">→</span> : null}
                        {e.newState ? <StatusBadge status={e.newState} /> : null}
                      </div>
                    </td>
                    <td className="text-xs text-slate-500">{e.actorType === "SYSTEM" ? "system" : e.actorType.toLowerCase()}</td>
                    <td>
                      {e.incidentId ? (
                        <Link href={`/incidents/${e.incidentId}`} className="font-mono text-xs text-brand-600 hover:underline">
                          {shortId(e.incidentId)}
                        </Link>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <Pagination page={page} totalPages={audit.data?.totalPages ?? 0} onChange={setPage} />
    </div>
  );
}
