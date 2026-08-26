"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { SystemHealth } from "@/lib/types";
import { PageHeader, Card, Skeleton, ToneDot } from "@/components/ui";

export default function SystemPage() {
  const health = useQuery({
    queryKey: ["system-health"],
    queryFn: () => api.get<SystemHealth>("/system/health"),
    refetchInterval: 10_000,
  });

  const h = health.data;

  return (
    <div>
      <PageHeader title="System Health" subtitle="Webhook pipeline, event backbone, AI service, and provider status." />

      {!h ? (
        <div className="space-y-3">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24" />)}</div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <Card title="Webhook pipeline">
            <div className="space-y-2 text-[13px]">
              <Row label="Received" value={String(h.webhookReceived)} />
              <Row label="Pending processing" value={String(h.webhookPending)} tone={h.webhookPending > 0 ? "amber" : "green"} />
              <Row label="Outbox pending" value={String(h.outboxPending)} tone={h.outboxPending > 0 ? "amber" : "green"} />
              <Row label="Dead-letter queue" value={String(h.deadLetterQueue)} tone={h.deadLetterQueue > 0 ? "red" : "green"} />
            </div>
          </Card>

          <Card title="AI decision service">
            <div className="space-y-2 text-[13px]">
              <Row label="Enabled" value={String(h.aiService.enabled)} />
              <Row label="Mode" value={h.aiService.mode === "llm" ? "LLM" : "deterministic fallback"} tone={h.aiService.mode === "llm" ? "green" : "amber"} />
              <Row label="Endpoint" value={h.aiService.baseUrl} mono />
              <Row label="Fallbacks used" value={String(h.metrics.aiFallbacks ?? 0)} />
            </div>
          </Card>

          <Card title="Payment provider">
            <div className="space-y-2 text-[13px]">
              <Row label="Provider" value={h.provider.name} />
              <Row label="Mode" value={h.provider.mode} tone={h.provider.mock ? "amber" : "green"} />
              <Row label="Event dispatch" value={h.eventDispatchMode} />
              <Row label="Temporal workflows" value={h.temporalEnabled ? "enabled" : "db scheduler"} />
              <Row label="Demo mode" value={String(h.demoMode)} />
            </div>
          </Card>

          <Card title="Safety metrics (since boot)">
            <div className="grid grid-cols-2 gap-3">
              <Metric label="Duplicate collections prevented" value={h.metrics.duplicateCollectionPrevented ?? 0} />
              <Metric label="Recoveries" value={h.metrics.recovered ?? 0} />
              <Metric label="Policy blocks" value={h.metrics.policyBlocks ?? 0} />
              <Metric label="Invalid signatures rejected" value={h.metrics.webhookInvalidSignatures ?? 0} />
            </div>
          </Card>

          <Card title="Runbooks" className="md:col-span-2">
            <div className="grid gap-2 text-[13px] md:grid-cols-2">
              {[
                ["Webhook processing failure", "docs/runbooks/webhook-processing-failure.md"],
                ["Provider outage", "docs/runbooks/provider-outage.md"],
                ["AI provider outage", "docs/runbooks/ai-provider-outage.md"],
                ["Kafka consumer lag", "docs/runbooks/kafka-consumer-lag.md"],
                ["DLQ replay", "docs/runbooks/dlq-replay.md"],
                ["Database degradation", "docs/runbooks/database-degradation.md"],
              ].map(([name, path]) => (
                <a key={path} href={`https://github.com/recoverai/recoverai/tree/main/${path}`} target="_blank" rel="noreferrer" className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-slate-700 hover:bg-slate-50">
                  <span className="font-mono text-[10px] text-slate-400">runbook</span> {name}
                </a>
              ))}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

function Row({ label, value, tone, mono }: { label: string; value: string; tone?: "green" | "amber" | "red"; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-slate-500">{label}</span>
      {tone ? (
        <ToneDot tone={tone} label={value} />
      ) : (
        <span className={`font-medium text-slate-800 ${mono ? "font-mono text-[11px]" : ""}`}>{value}</span>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="text-[10px] uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-0.5 text-xl font-semibold tabular-nums text-slate-900">{value}</div>
    </div>
  );
}
