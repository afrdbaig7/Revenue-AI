"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import type { IncidentDetail, AuditEvent } from "@/lib/types";
import { formatINR, formatDateTime, timeAgo, shortId } from "@/lib/format";
import { PageHeader, Card, StatusBadge, Button, Skeleton, EmptyState, ToneDot } from "@/components/ui";

function Timeline({ events }: { events: AuditEvent[] }) {
  if (!events.length) return <EmptyState title="No audit events for this incident" />;
  const ordered = [...events].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
  return (
    <ol className="relative space-y-4 border-l border-slate-200 pl-5">
      {ordered.map((e) => (
        <li key={e.id} className="relative">
          <span className="absolute -left-[25.5px] top-1 h-2.5 w-2.5 rounded-full border-2 border-white bg-brand-500 ring-1 ring-slate-200" />
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[13px] font-semibold text-slate-800">{e.eventType.replaceAll("_", " ")}</span>
            <StatusBadge status={e.newState} />
            {e.previousState && e.newState ? (
              <span className="text-[11px] text-slate-400">{e.previousState.replaceAll("_", " ")} →</span>
            ) : null}
            <span className="ml-auto text-[11px] text-slate-400">{formatDateTime(e.timestamp)}</span>
          </div>
          <div className="mt-0.5 flex flex-wrap gap-x-4 text-[11px] text-slate-500">
            <span>actor: {e.actorType === "SYSTEM" ? "system" : shortId(e.actorId)}</span>
            <span>entity: {e.entityType}</span>
            {e.correlationId ? <span className="font-mono">corr: {shortId(e.correlationId)}</span> : null}
          </div>
          {e.metadata ? (
            <pre className="mt-1 max-h-24 overflow-auto rounded-md bg-slate-50 p-2 text-[10px] text-slate-500">
              {JSON.stringify(e.metadata, null, 1)}
            </pre>
          ) : null}
        </li>
      ))}
    </ol>
  );
}

export default function IncidentDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const detail = useQuery({
    queryKey: ["incident", params.id],
    queryFn: () => api.get<IncidentDetail>(`/incidents/${params.id}`),
  });

  const audit = useQuery({
    queryKey: ["audit", params.id],
    queryFn: () => api.get<{ items: AuditEvent[] }>(`/audit-events?incidentId=${params.id}&size=100`),
  });

  const reprocess = useMutation({
    mutationFn: () => api.post(`/incidents/${params.id}/reprocess`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incident", params.id] });
      queryClient.invalidateQueries({ queryKey: ["summary"] });
    },
  });

  const cancel = useMutation({
    mutationFn: () => api.post(`/incidents/${params.id}/cancel`, { reason: "cancelled from dashboard" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incident", params.id] });
      queryClient.invalidateQueries({ queryKey: ["summary"] });
    },
  });

  if (detail.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-40" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  const d = detail.data;
  if (!d) return <EmptyState title="Incident not found" />;
  const i = d.incident;
  const terminal = ["RECOVERED", "FAILED", "BLOCKED", "CLOSED", "LATE_AUTHORIZED", "OPTED_OUT", "EXPIRED", "CANCELLED"].includes(i.status);

  return (
    <div>
      <PageHeader
        title={`Incident ${shortId(i.id)}`}
        subtitle={`${i.incidentType.replaceAll("_", " ")} · detected ${timeAgo(i.detectedAt ?? i.createdAt)}`}
        actions={
          <>
            {!terminal ? (
              <>
                <Button variant="secondary" onClick={() => reprocess.mutate()} disabled={reprocess.isPending}>
                  Reprocess
                </Button>
                <Button variant="danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
                  Cancel recovery
                </Button>
              </>
            ) : null}
            <Button variant="secondary" onClick={() => router.push("/incidents")}>Back</Button>
          </>
        }
      />

      {/* Headline */}
      <div className="card p-5">
        <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Amount at risk</div>
            <div className="text-3xl font-semibold tabular-nums tracking-tight text-slate-900">{formatINR(i.amountMinor)}</div>
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Failure</div>
            <div className="text-sm font-semibold text-slate-800">{i.failureCategory?.replaceAll("_", " ") ?? "—"}</div>
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Diagnosis confidence</div>
            <div className="text-sm font-semibold tabular-nums text-slate-800">
              {i.confidence != null ? `${(i.confidence * 100).toFixed(0)}%` : "—"}
              {i.diagnosisLayer ? <span className="ml-1.5 text-[10px] font-medium text-slate-400">({i.diagnosisLayer})</span> : null}
            </div>
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Status</div>
            <StatusBadge status={i.status} />
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Selected strategy</div>
            <div className="text-sm font-semibold text-slate-800">{i.selectedStrategy?.replaceAll("_", " ") ?? "—"}</div>
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Recovered</div>
            <div className="text-sm font-semibold tabular-nums text-emerald-600">
              {formatINR(i.recoveredAmountMinor)}
              {i.netRecoveredMinor != null && i.netRecoveredMinor > 0 ? (
                <span className="ml-1 text-[10px] text-slate-400">net {formatINR(i.netRecoveredMinor)}</span>
              ) : null}
            </div>
          </div>
        </div>
        {i.cancellationReason ? (
          <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            <strong>Stopped:</strong> {i.cancellationReason}
          </div>
        ) : null}
        {i.policyResult ? (
          <div className="mt-2 text-[11px] text-slate-400">
            Policy result: <span className="font-mono">{i.policyResult}</span> · window ends {formatDateTime(i.recoveryWindowEndsAt)}
          </div>
        ) : null}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {/* Explainability: diagnosis + decision */}
        <Card title="RecoverAI reasoning">
          {d.diagnoses.length === 0 ? (
            <EmptyState title="No diagnosis recorded" hint="Re-process the incident to generate one." />
          ) : (
            <div className="space-y-4">
              {d.diagnoses.map((diag) => (
                <div key={diag.id} className="rounded-lg border border-slate-200 p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-800">{diag.layer}</span>
                    <span className="text-xs tabular-nums text-slate-500">confidence {(diag.confidence * 100).toFixed(0)}%</span>
                  </div>
                  <div className="mt-1 text-sm font-semibold text-slate-900">{diag.failureCategory.replaceAll("_", " ")}</div>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {diag.evidence.map((ev) => (
                      <span key={ev} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-600">{ev}</span>
                    ))}
                  </div>
                  <div className="mt-2 text-[11px] text-slate-400">
                    model {diag.modelVersion ?? "—"} · prompt {diag.promptVersion ?? "—"}
                  </div>
                </div>
              ))}

              {d.decision ? (
                <div>
                  <div className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Candidate strategies (expected value) — ranked by {d.decision.rankingSource}
                  </div>
                  <div className="space-y-1.5">
                    {d.decision.candidates.map((c, idx) => (
                      <div
                        key={c.strategy}
                        className={`flex items-center justify-between rounded-lg border px-3 py-2 text-xs ${
                          idx === 0 ? "border-emerald-200 bg-emerald-50/60" : "border-slate-200"
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-[10px] text-slate-400">#{idx + 1}</span>
                          <span className="font-semibold text-slate-800">{c.strategy.replaceAll("_", " ")}</span>
                          <span className="hidden text-[10px] text-slate-400 md:inline">{c.rationale}</span>
                        </div>
                        <div className="flex items-center gap-3 tabular-nums">
                          <span className="text-slate-500">{(c.probability * 100).toFixed(0)}%</span>
                          <span className="font-semibold text-slate-900">{formatINR(c.expectedValueMinor, { noDecimals: true })}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                  {d.decision.reason ? <div className="mt-2 text-[11px] text-slate-500">Why: {d.decision.reason}</div> : null}
                </div>
              ) : null}
            </div>
          )}
        </Card>

        {/* Actions */}
        <Card title="Recovery actions">
          {d.actions.length === 0 ? (
            <EmptyState title="No actions scheduled" />
          ) : (
            <div className="space-y-2">
              {d.actions.map((a) => (
                <div key={a.id} className="rounded-lg border border-slate-200 p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-800">{a.strategy.replaceAll("_", " ")}</span>
                    <StatusBadge status={a.status} />
                  </div>
                  <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-0.5 text-[11px] text-slate-500">
                    <span>attempt #{a.attemptNumber}</span>
                    <span>scheduled {formatDateTime(a.scheduledFor)}</span>
                    {a.executedAt ? <span>executed {formatDateTime(a.executedAt)}</span> : null}
                    {a.providerReference ? <span className="font-mono">{a.providerReference}</span> : null}
                  </div>
                  {a.result ? <div className="mt-1 text-[11px] text-slate-600">result: {a.result}</div> : null}
                  {a.error ? <div className="mt-1 text-[11px] text-rose-600">error: {a.error}</div> : null}
                  <div className="mt-1 font-mono text-[10px] text-slate-400">key: {a.idempotencyKey}</div>
                </div>
              ))}
            </div>
          )}

          {d.communications.length > 0 ? (
            <div className="mt-4">
              <div className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500">Customer communications</div>
              <div className="space-y-1.5">
                {d.communications.map((c) => (
                  <div key={c.id} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-xs">
                    <span className="text-slate-700">{c.channel}</span>
                    <span className="text-[10px] text-slate-400">
                      {c.status}{c.simulated ? " · SIMULATED" : ""}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </Card>
      </div>

      {/* Timeline */}
      <Card title="Audit timeline (immutable)" className="mt-4">
        {audit.isLoading ? <Skeleton className="h-48" /> : <Timeline events={audit.data?.items ?? []} />}
      </Card>
    </div>
  );
}
