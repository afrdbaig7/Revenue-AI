"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import type { Experiment } from "@/lib/types";
import { formatINR, formatPercent } from "@/lib/format";
import { PageHeader, Card, Skeleton, EmptyState, Button, SyntheticBanner } from "@/components/ui";
import { useState } from "react";

export default function ExperimentsPage() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const experiments = useQuery({
    queryKey: ["experiments"],
    queryFn: () => api.get<Experiment[]>("/experiments"),
  });

  const run = useMutation({
    mutationFn: () =>
      api.post<Experiment>("/experiments", {
        name: `Batch evaluation — ${new Date().toLocaleString("en-IN")}`,
        description: "Same seeded population through CONTROL (fixed baseline) and TREATMENT (RecoverAI).",
        seed: 42,
        populationSize: 1000,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["experiments"] });
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Experiment failed"),
  });

  return (
    <div>
      <PageHeader
        title="Experiments — RecoverAI vs Baseline"
        subtitle="Reproducible batch evaluation on identical seeded populations."
        actions={<Button onClick={() => run.mutate()} disabled={run.isPending}>{run.isPending ? "Running…" : "Run 1,000-incident batch"}</Button>}
      />
      <SyntheticBanner />
      {error ? <div className="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">{error}</div> : null}

      {experiments.isLoading ? (
        <div className="space-y-3">{Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-40" />)}</div>
      ) : !experiments.data?.length ? (
        <EmptyState title="No experiments yet" hint="Run a batch to compare RecoverAI against the fixed baseline." />
      ) : (
        <div className="space-y-4">
          {experiments.data.map((exp) => {
            const r = exp.results;
            return (
              <Card key={exp.id} title={exp.name}>
                {!r ? (
                  <EmptyState title="Experiment has no results" />
                ) : (
                  <div>
                    <div className="mb-3 flex flex-wrap gap-x-6 gap-y-1 text-[11px] text-slate-500">
                      <span>seed {exp.seed}</span>
                      <span>population {exp.populationSize}</span>
                      <span>completed {exp.completedAt ? new Date(exp.completedAt).toLocaleString("en-IN") : "—"}</span>
                      <span className="text-amber-600">{r.label}</span>
                    </div>

                    <div className="grid gap-3 md:grid-cols-2">
                      {/* Control */}
                      <div className="rounded-xl border border-slate-200 p-4">
                        <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Control — fixed baseline</div>
                        <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-[13px]">
                          <Metric label="Recovery rate" value={formatPercent(r.control.recoveryRatePercent)} />
                          <Metric label="95% CI" value={`${r.control.recoveryRateLowerCI.toFixed(1)}–${r.control.recoveryRateUpperCI.toFixed(1)}%`} />
                          <Metric label="Gross recovered" value={formatINR(r.control.grossRecoveredMinor, { noDecimals: true })} />
                          <Metric label="Net recovered" value={formatINR(r.control.netRecoveredMinor, { noDecimals: true })} />
                          <Metric label="Avg attempts" value={String(r.control.avgAttempts)} />
                          <Metric label="Avg contacts" value={String(r.control.avgContacts)} />
                          <Metric label="Unnecessary contacts" value={String(r.control.unnecessaryContacts)} />
                          <Metric label="Time to recovery" value={`${r.control.avgTimeToRecoveryHours.toFixed(1)}h`} />
                        </div>
                      </div>

                      {/* Treatment */}
                      <div className="rounded-xl border border-emerald-200 bg-emerald-50/40 p-4">
                        <div className="text-[11px] font-semibold uppercase tracking-wide text-emerald-700">Treatment — RecoverAI</div>
                        <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-[13px]">
                          <Metric label="Recovery rate" value={formatPercent(r.treatment.recoveryRatePercent)} strong />
                          <Metric label="95% CI" value={`${r.treatment.recoveryRateLowerCI.toFixed(1)}–${r.treatment.recoveryRateUpperCI.toFixed(1)}%`} />
                          <Metric label="Gross recovered" value={formatINR(r.treatment.grossRecoveredMinor, { noDecimals: true })} strong />
                          <Metric label="Net recovered" value={formatINR(r.treatment.netRecoveredMinor, { noDecimals: true })} strong />
                          <Metric label="Avg attempts" value={String(r.treatment.avgAttempts)} />
                          <Metric label="Avg contacts" value={String(r.treatment.avgContacts)} />
                          <Metric label="Unnecessary contacts" value={String(r.treatment.unnecessaryContacts)} />
                          <Metric label="Time to recovery" value={`${r.treatment.avgTimeToRecoveryHours.toFixed(1)}h`} />
                        </div>
                      </div>
                    </div>

                    {/* Delta strip */}
                    <div className="mt-3 flex flex-wrap gap-x-8 gap-y-2 rounded-lg bg-slate-900 px-4 py-3 text-[12px] text-slate-300">
                      <span>Δ recovery rate <strong className="text-emerald-400">+{r.delta.recoveryRatePoints.toFixed(1)} pts</strong></span>
                      <span>Δ net recovered <strong className="text-emerald-400">{formatINR(r.delta.incrementalRecoveredMinor, { noDecimals: true })}</strong></span>
                      <span>attempts saved <strong className="text-emerald-400">{Math.max(0, r.delta.attemptsSaved)}</strong></span>
                      <span>unnecessary contacts saved <strong className="text-emerald-400">{Math.max(0, r.delta.unnecessaryContactsSaved)}</strong></span>
                    </div>
                    <div className="mt-2 text-[11px] text-slate-400">{r.methodology}</div>
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Metric({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wide text-slate-400">{label}</div>
      <div className={`tabular-nums ${strong ? "font-semibold text-slate-900" : "text-slate-700"}`}>{value}</div>
    </div>
  );
}
