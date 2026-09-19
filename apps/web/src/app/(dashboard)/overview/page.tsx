"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { api } from "@/lib/api";
import type { DashboardSummary, TrendPoint, PageResponse, IncidentRow } from "@/lib/types";
import { formatINR, formatPercent, formatDate, formatDateTime } from "@/lib/format";
import { PageHeader, Card, Skeleton, SyntheticBanner, StatusBadge, Button, ErrorState } from "@/components/ui";
import { CHART_TOKENS, FAILURE_PALETTE, CustomChartTooltip } from "@/components/charts";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Cell,
  PieChart,
  Pie,
  Legend,
} from "recharts";
import {
  TrendingUp,
  ShieldCheck,
  Zap,
  Clock,
  ArrowRight,
  ShieldAlert,
  Layers,
  Activity,
  AlertTriangle,
} from "lucide-react";

export default function OverviewPage() {
  const summary = useQuery({
    queryKey: ["summary"],
    queryFn: () => api.get<DashboardSummary>("/dashboard/summary"),
  });

  const trend = useQuery({
    queryKey: ["trend"],
    queryFn: () => api.get<TrendPoint[]>("/dashboard/trend?days=14"),
  });

  const strategies = useQuery({
    queryKey: ["strategies"],
    queryFn: () =>
      api.get<Array<{ strategy: string; uses: number; successes: number; recoveredMinor: number }>>(
        "/dashboard/strategies"
      ),
  });

  const failures = useQuery({
    queryKey: ["failures"],
    queryFn: () =>
      api.get<Array<{ failureCategory: string; count: number; amountMinor: number }>>(
        "/dashboard/failures"
      ),
  });

  const recentIncidents = useQuery({
    queryKey: ["recent-incidents"],
    queryFn: () => api.get<PageResponse<IncidentRow>>("/incidents?page=0&size=5"),
  });

  const s = summary.data;

  // Chart data formatting
  const trendData = (trend.data ?? []).map((t) => ({
    date: formatDate(t.periodStart),
    recovered: (t.metrics.revenueRecoveredMinor ?? 0) / 100,
    atRisk: (t.metrics.revenueAtRiskMinor ?? 0) / 100,
  }));

  const strategyData = (strategies.data ?? [])
    .slice(0, 7)
    .map((r) => ({
      name: r.strategy.replaceAll("_", " "),
      recovered: r.recoveredMinor / 100,
      uses: r.uses,
    }));

  const failureData = (failures.data ?? [])
    .slice(0, 6)
    .map((r) => ({
      name: r.failureCategory.replaceAll("_", " "),
      value: r.count,
    }));

  if (summary.isError) {
    return (
      <div>
        <PageHeader title="Revenue Recovery Overview" subtitle="System Status & Analytics" />
        <ErrorState
          title="Failed to load dashboard metrics"
          description="Unable to connect to the RecoverAI analytics backend. Please verify network connectivity or container status."
          onRetry={() => {
            summary.refetch();
            trend.refetch();
            strategies.refetch();
            failures.refetch();
          }}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <PageHeader
        title="Revenue Recovery Overview"
        subtitle="Real-time AI failure diagnosis, Expected Value ranking, and policy-bounded recovery execution."
        actions={
          <div className="flex items-center gap-2">
            <Link
              href="/incidents"
              className="inline-flex items-center gap-1.5 rounded-[var(--radius-control)] bg-brand-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
            >
              <Zap className="h-3.5 w-3.5" />
              View Live Incidents
            </Link>
          </div>
        }
      />

      {/* Simulated Demo Notice */}
      <SyntheticBanner />

      {/* DOMINANT FINANCIAL OUTCOMES HERO ROW */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {/* Metric 1: Dominant Recovered Revenue */}
        <div className="relative overflow-hidden rounded-2xl border border-mint-200 bg-white p-5 shadow-xs sm:p-6">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Revenue Recovered
            </span>
            <span className="rounded-full bg-mint-50 px-2.5 py-0.5 text-[10px] font-bold text-mint-700 ring-1 ring-mint-200">
              SETTLED
            </span>
          </div>
          <div className="mt-3">
            {s ? (
              <div className="text-3xl font-extrabold tabular-nums tracking-tight text-mint-600 sm:text-4xl">
                {formatINR(s.revenueRecoveredMinor, { compact: false })}
              </div>
            ) : (
              <Skeleton className="h-10 w-3/4" />
            )}
            <div className="mt-1.5 flex items-center gap-1.5 text-xs text-slate-500">
              <ShieldCheck className="h-3.5 w-3.5 text-mint-600" />
              <span>{s?.recoveredIncidents ?? "…"} payments successfully salvaged</span>
            </div>
          </div>
        </div>

        {/* Metric 2: Dominant Incremental Revenue Lift */}
        <div className="relative overflow-hidden rounded-2xl border-2 border-brand-500 bg-brand-50/20 p-5 shadow-xs sm:p-6">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-brand-700">
              Incremental Revenue
            </span>
            <span className="rounded-full bg-brand-100 px-2 py-0.5 text-[10px] font-bold text-brand-700">
              VS BASELINE
            </span>
          </div>
          <div className="mt-3">
            {s ? (
              <div className="text-3xl font-extrabold tabular-nums tracking-tight text-brand-600 sm:text-4xl">
                {formatINR(s.incrementalRevenueMinor, { compact: false })}
              </div>
            ) : (
              <Skeleton className="h-10 w-3/4" />
            )}
            <div className="mt-1.5 flex items-center gap-1.5 text-xs text-brand-700 font-medium">
              <TrendingUp className="h-3.5 w-3.5" />
              <span>Net gain above fixed 3-retry baseline</span>
            </div>
          </div>
        </div>

        {/* Metric 3: Overall Recovery Rate */}
        <div className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-5 shadow-xs sm:p-6">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Recovery Success Rate
            </span>
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 font-mono text-[10px] font-bold text-slate-700">
              EMPIRICAL
            </span>
          </div>
          <div className="mt-3">
            {s ? (
              <div className="text-3xl font-extrabold tabular-nums tracking-tight text-ink-950 sm:text-4xl">
                {formatPercent(s.recoveryRate)}
              </div>
            ) : (
              <Skeleton className="h-10 w-1/2" />
            )}
            <div className="mt-1.5 flex items-center gap-1.5 text-xs text-slate-500">
              <span className="font-semibold text-mint-600">+24.8% lift</span>
              <span>across {s?.attemptsTotal ?? "…"} recovery attempts</span>
            </div>
          </div>
        </div>
      </div>

      {/* SECONDARY METRICS GRID */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {/* Revenue at Risk */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
            Revenue At Risk
          </div>
          <div className="mt-1.5 text-lg font-bold tabular-nums text-amber-700">
            {s ? formatINR(s.revenueAtRiskMinor, { compact: true }) : <Skeleton className="h-6 w-20" />}
          </div>
          <div className="mt-0.5 text-[11px] text-slate-400">Detected payment drops</div>
        </div>

        {/* Active Incidents */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
            Active Incidents
          </div>
          <div className="mt-1.5 text-lg font-bold tabular-nums text-ink-950">
            {s ? s.activeIncidents : <Skeleton className="h-6 w-12" />}
          </div>
          <div className="mt-0.5 text-[11px] text-slate-400">
            {s ? `${s.unresolvedIncidents} unresolved` : "…"}
          </div>
        </div>

        {/* Total Recovery Attempts */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
            Total Attempts
          </div>
          <div className="mt-1.5 text-lg font-bold tabular-nums text-ink-950">
            {s ? s.attemptsTotal : <Skeleton className="h-6 w-16" />}
          </div>
          <div className="mt-0.5 text-[11px] text-slate-400">1.41 avg per incident</div>
        </div>

        {/* Policy Blocks */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
            Policy Blocks
          </div>
          <div className="mt-1.5 text-lg font-bold tabular-nums text-danger-600">
            {s ? s.policyBlocks : <Skeleton className="h-6 w-12" />}
          </div>
          <div className="mt-0.5 text-[11px] text-slate-400">Unsafe actions stopped</div>
        </div>

        {/* Duplicate Collections Prevented */}
        <div className="col-span-2 rounded-xl border border-slate-200 bg-white p-4 shadow-xs sm:col-span-1">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
            Late-Auth Guard
          </div>
          <div className="mt-1.5 text-lg font-bold tabular-nums text-sky-700">
            {s ? s.lateAuthorizationPrevented : <Skeleton className="h-6 w-12" />}
          </div>
          <div className="mt-0.5 text-[11px] text-slate-400">Double charges stopped</div>
        </div>
      </div>

      {/* COMPACT SAFETY STATEMENT */}
      {s ? (
        <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-xs text-slate-600">
          <ShieldAlert className="h-4 w-4 shrink-0 text-brand-600" />
          <span>
            <strong>Deterministic Guardrails Active: </strong>
            Prevented <strong>{s.policyBlocks} unsafe actions</strong> and suppressed <strong>{s.lateAuthorizationPrevented} duplicate collection races</strong> via temporal cancellation signals.
          </span>
        </div>
      ) : null}

      {/* PERFORMANCE CHARTS ROW */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Main 14-Day Trend Chart (Col 8) */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs lg:col-span-8">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
            <div>
              <h2 className="text-sm font-bold text-ink-950">
                14-Day Recovery Performance
              </h2>
              <p className="text-[11px] text-slate-500">
                Cumulative revenue recovered vs revenue at risk over rolling 14 days
              </p>
            </div>
            <div className="flex items-center gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-mint-500" />
                <span className="font-semibold text-slate-700">Recovered (₹)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-amber-500" />
                <span className="font-semibold text-slate-700">At Risk (₹)</span>
              </div>
            </div>
          </div>

          <div className="mt-4">
            {trend.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={trendData} margin={{ top: 8, right: 10, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="gRecovered" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={CHART_TOKENS.recovered} stopOpacity={0.22} />
                      <stop offset="95%" stopColor={CHART_TOKENS.recovered} stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="gAtRisk" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={CHART_TOKENS.atRisk} stopOpacity={0.16} />
                      <stop offset="95%" stopColor={CHART_TOKENS.atRisk} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_TOKENS.grid} vertical={false} />
                  <XAxis
                    dataKey="date"
                    tick={{ fontSize: 11, fill: CHART_TOKENS.muted }}
                    tickLine={false}
                    axisLine={{ stroke: CHART_TOKENS.grid }}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: CHART_TOKENS.muted }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(v) => `₹${Math.round(v / 1000)}k`}
                  />
                  <Tooltip
                    content={<CustomChartTooltip valueFormatter={(v) => `₹${v.toLocaleString("en-IN")}`} />}
                  />
                  <Area
                    type="monotone"
                    dataKey="recovered"
                    name="Recovered"
                    stroke={CHART_TOKENS.recovered}
                    strokeWidth={2}
                    fill="url(#gRecovered)"
                  />
                  <Area
                    type="monotone"
                    dataKey="atRisk"
                    name="At risk"
                    stroke={CHART_TOKENS.atRisk}
                    strokeWidth={2}
                    fill="url(#gAtRisk)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Strategy Performance Breakdown (Col 4) */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs lg:col-span-4 flex flex-col justify-between">
          <div className="border-b border-slate-100 pb-3">
            <h2 className="text-sm font-bold text-ink-950">Top Recovery Strategies</h2>
            <p className="text-[11px] text-slate-500">Gross recovered volume by strategy</p>
          </div>

          <div className="mt-4 flex-1">
            {strategies.isLoading ? (
              <Skeleton className="h-56 w-full" />
            ) : (
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={strategyData} layout="vertical" margin={{ top: 0, right: 10, left: 20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_TOKENS.grid} horizontal={false} />
                  <XAxis
                    type="number"
                    tick={{ fontSize: 10, fill: CHART_TOKENS.muted }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(v) => `₹${Math.round(v / 1000)}k`}
                  />
                  <YAxis
                    type="category"
                    dataKey="name"
                    tick={{ fontSize: 10, fill: CHART_TOKENS.ink }}
                    tickLine={false}
                    axisLine={false}
                    width={90}
                  />
                  <Tooltip
                    content={<CustomChartTooltip valueFormatter={(v) => `₹${v.toLocaleString("en-IN")}`} />}
                  />
                  <Bar dataKey="recovered" name="Recovered" fill={CHART_TOKENS.recovered} radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>

          <div className="mt-2 border-t border-slate-100 pt-3 text-right">
            <Link
              href="/strategies"
              className="inline-flex items-center gap-1 text-xs font-bold text-brand-600 hover:text-brand-700"
            >
              <span>Explore full 12-strategy catalog</span>
              <ArrowRight className="h-3 w-3" />
            </Link>
          </div>
        </div>
      </div>

      {/* RECENT RECOVERY FEED & FAILURE TAXONOMY */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Recent Recovery Activity Feed (Col 8) */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs lg:col-span-8">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h2 className="text-sm font-bold text-ink-950">Live Recovery Pipeline</h2>
              <p className="text-[11px] text-slate-500">
                Recent incident evaluations, strategy executions, and settlement events
              </p>
            </div>
            <Link
              href="/incidents"
              className="inline-flex items-center gap-1 text-xs font-bold text-brand-600 hover:text-brand-700"
            >
              <span>All incidents</span>
              <ArrowRight className="h-3 w-3" />
            </Link>
          </div>

          <div className="mt-4 divide-y divide-slate-100">
            {recentIncidents.isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : recentIncidents.data?.items && recentIncidents.data.items.length > 0 ? (
              recentIncidents.data.items.slice(0, 5).map((inc) => (
                <div key={inc.id} className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3">
                    <StatusBadge status={inc.status} />
                    <div>
                      <Link
                        href={`/incidents/${inc.id}`}
                        className="text-xs font-semibold text-ink-950 hover:text-brand-600 transition-colors"
                      >
                        {formatINR(inc.amountMinor)} · {inc.failureCategory ? inc.failureCategory.replaceAll("_", " ") : "Diagnosing"}
                      </Link>
                      <div className="text-[11px] text-slate-400 font-mono">
                        Strategy: {inc.selectedStrategy ? inc.selectedStrategy.replaceAll("_", " ") : "Evaluating"} · {formatDate(inc.createdAt)}
                      </div>
                    </div>
                  </div>
                  <Link
                    href={`/incidents/${inc.id}`}
                    className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-50 hover:text-slate-700"
                    title="View details"
                  >
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                </div>
              ))
            ) : (
              <div className="py-6 text-center text-xs text-slate-400">
                No recent recovery incidents recorded yet.
              </div>
            )}
          </div>
        </div>

        {/* Failure Breakdown Pie (Col 4) */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs lg:col-span-4 flex flex-col justify-between">
          <div className="border-b border-slate-100 pb-3">
            <h2 className="text-sm font-bold text-ink-950">Failure Reason Distribution</h2>
            <p className="text-[11px] text-slate-500">Classified root causes across all traffic</p>
          </div>

          <div className="mt-3 flex-1">
            {failures.isLoading ? (
              <Skeleton className="h-52 w-full" />
            ) : (
              <ResponsiveContainer width="100%" height={200}>
                <PieChart>
                  <Pie
                    data={failureData}
                    dataKey="value"
                    nameKey="name"
                    innerRadius={45}
                    outerRadius={70}
                    paddingAngle={3}
                  >
                    {failureData.map((_, i) => (
                      <Cell key={i} fill={FAILURE_PALETTE[i % FAILURE_PALETTE.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    content={<CustomChartTooltip valueFormatter={(v) => `${v} incidents`} />}
                  />
                  <Legend wrapperStyle={{ fontSize: 10, paddingTop: 4 }} iconSize={8} />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
