"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import type { Approval, PageResponse } from "@/lib/types";
import { formatINR, timeAgo, shortId } from "@/lib/format";
import { PageHeader, Card, StatusBadge, EmptyState, Button } from "@/components/ui";
import { useState } from "react";
import Link from "next/link";

export default function ApprovalsPage() {
  const queryClient = useQueryClient();
  const [note, setNote] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const approvals = useQuery({
    queryKey: ["approvals"],
    queryFn: () => api.get<PageResponse<Approval>>("/approvals?status=PENDING&size=50"),
  });

  const decide = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "approve" | "reject" }) =>
      api.post(`/approvals/${id}/${action}`, { note: note[id] }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["approvals"] });
      queryClient.invalidateQueries({ queryKey: ["summary"] });
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Decision failed"),
  });

  const rows = approvals.data?.items ?? [];

  return (
    <div>
      <PageHeader
        title="Approval Queue"
        subtitle="High-value, discount, and low-confidence recovery proposals need a human decision. Every decision is audited."
      />
      {error ? <div className="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">{error}</div> : null}

      {approvals.isLoading ? (
        <div className="space-y-3">{Array.from({ length: 2 }).map((_, i) => <div key={i} className="skeleton h-36" />)}</div>
      ) : rows.length === 0 ? (
        <EmptyState title="Approval queue is empty" hint="New proposals appear here when policy requires human approval." />
      ) : (
        <div className="space-y-4">
          {rows.map((a) => (
            <Card key={a.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <Link href={`/incidents/${a.incidentId}`} className="font-mono text-xs font-semibold text-brand-600 hover:underline">
                      incident {shortId(a.incidentId)}
                    </Link>
                    <StatusBadge status={a.status} />
                    {a.proposal.lowConfidence ? (
                      <span className="rounded-md bg-amber-50 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 ring-1 ring-inset ring-amber-200">
                        LOW CONFIDENCE
                      </span>
                    ) : null}
                  </div>
                  <div className="mt-2 flex flex-wrap gap-x-8 gap-y-1 text-sm">
                    <div>
                      <div className="text-[10px] uppercase tracking-wide text-slate-400">Proposed action</div>
                      <div className="font-semibold text-slate-800">{a.proposal.strategy?.replaceAll("_", " ") ?? "—"}</div>
                    </div>
                    <div>
                      <div className="text-[10px] uppercase tracking-wide text-slate-400">Amount</div>
                      <div className="font-semibold tabular-nums">{formatINR(a.proposal.amountMinor ?? 0)}</div>
                    </div>
                    <div>
                      <div className="text-[10px] uppercase tracking-wide text-slate-400">Failure</div>
                      <div className="text-slate-700">{a.proposal.failureCategory?.replaceAll("_", " ") ?? "—"}</div>
                    </div>
                    <div>
                      <div className="text-[10px] uppercase tracking-wide text-slate-400">Diagnosis confidence</div>
                      <div className="tabular-nums text-slate-700">
                        {a.proposal.confidence != null ? `${(a.proposal.confidence * 100).toFixed(0)}%` : "—"}
                      </div>
                    </div>
                    <div>
                      <div className="text-[10px] uppercase tracking-wide text-slate-400">Requested</div>
                      <div className="text-slate-500">{timeAgo(a.proposal.requestedAt)}</div>
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    value={note[a.id] ?? ""}
                    onChange={(e) => setNote((n) => ({ ...n, [a.id]: e.target.value }))}
                    placeholder="Decision note (optional)"
                    className="w-48 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-brand-100"
                  />
                  <Button variant="danger" onClick={() => decide.mutate({ id: a.id, action: "reject" })} disabled={decide.isPending}>
                    Reject
                  </Button>
                  <Button onClick={() => decide.mutate({ id: a.id, action: "approve" })} disabled={decide.isPending}>
                    Approve
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
