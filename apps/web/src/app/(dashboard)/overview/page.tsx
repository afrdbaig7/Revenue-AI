"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { DashboardSummary, TrendPoint } from "@/lib/types";
import { formatINR, formatPercent, formatDate } from "@/lib/format";
import { StatCard, PageHeader, Card, Skeleton, SyntheticBanner } from "@/components/ui";
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

const STRATEGY_COLORS = ["#2563eb", "#10b981", "#f59e0b", "#8b5cf6", "#f43f5e", "#06b6d4", "#64748b"];
const FAILURE_COLORS = ["#f43f5e", "#f59e0b", "#8b5cf6", "#06b6d4", "#10b981", "#64748b", "#2563eb"];

export default function OverviewPage() {
  const summary = useQuery({ queryKey: ["summary"], queryFn: () => api.get<DashboardSummary>("/dashboard/summary") });
  const trend = useQuery({ queryKey: ["trend"], queryFn: () => api.get<TrendPoint[]>("/dashboard/trend?days=14") });
  const strategies = useQuery({
    queryKey: ["strategies"],
    queryFn: () => api.get<Array<{ strategy: string; uses: number; successes: number; recoveredMinor: number }>>("/dashboard/strategies"),
  });
  const failures = useQuery({
    queryKey: ["failures"],
    queryFn: () => api.get<Array<{ failureCategory: string; count: number; amountMinor: number }>>("/dashboard/failures"),
  });

  const s = summary.data;

  const trendData = (trend.data ?? []).map((t) => ({
    date: formatDate(t.periodStart),
    recovered: (t.metrics.revenueRecoveredMinor ?? 0) / 100,
    atRisk: (t.metrics.revenueAtRiskMinor ?? 0) / 100,
  }));

  const strategyData = (strategies.data ?? [])
    .slice(0, 7)
    .map((r) => ({ name: r.strategy.replaceAll("_", " "), recovered: r.recoveredMinor / 100 }));

  const failureData = (failures.data ?? [])
    .slice(0, 7)
    .map((r) => ({ name: r.failureCategory.replaceAll("_", " "), value: r.count }));

  return (
    <div>
      <PageHeader
        title="Revenue Recovery Overview"
        subtitle="Detect → diagnose → recover → measure. All figures below are SIMULATED."
      />

      <SyntheticBanner />

      {/* Headline cards */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {s ? (
          <>
            <StatCard label="Revenue at Risk" value={formatINR(s.revenueAtRiskMinor, { compact: true })} accent="amber" sub={`${s.activeIncidents} active incidents`} />
            <StatCard label="Revenue Recovered" value={formatINR(s.revenueRecoveredMinor, { compact: true })} accent="green" sub={`${s.recoveredIncidents} successful recoveries`} />
            <StatCard label="Incremental Revenue" value={formatINR(s.incrementalRevenueMinor, { compact: true })} accent="blue" sub="vs fixed baseline (simulated)" />
            <StatCard label="Recovery Rate" value={formatPercent(s.recoveryRate)} accent="slate" sub={`${s.attemptsTotal} attempts · ${s.policyBlocks} policy blocks`} />
          </>
        ) : (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24" />)
        )}
      </div>

      {/* Secondary strip */}
      <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {s ? (
          <>
            <StatCard label="Unresolved Incidents" value={String(s.unresolvedIncidents)} />
            <StatCard label="Recovery Attempts" value={String(s.attemptsTotal)} />
            <StatCard label="Policy Blocks" value={String(s.policyBlocks)} accent="red" sub="Unsafe actions prevented" />
            <StatCard label="Duplicate Collections Prevented" value={String(s.lateAuthorizationPrevented)} accent="amber" sub="Late-authorization stops" />
          </>
        ) : (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24" />)
        )}
      </div>

      {/* Charts */}
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card title="Recovered revenue over time (₹)" className="lg:col-span-2">
          {trend.isLoading ? (
            <Skeleton className="h-64" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <AreaChart data={trendData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="gRecovered" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="gAtRisk" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={{ stroke: "#e2e8f0" }} />
                <YAxis tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={false} tickFormatter={(v) => `₹${Math.round(v / 1000)}k`} />
                <Tooltip formatter={(v: number) => `₹${v.toLocaleString("en-IN")}`} contentStyle={{ borderRadius: 8, border: "1px solid #e2e8f0", fontSize: 12 }} />
                <Area type="monotone" dataKey="recovered" name="Recovered" stroke="#10b981" strokeWidth={2} fill="url(#gRecovered)" />
                <Area type="monotone" dataKey="atRisk" name="At risk" stroke="#f59e0b" strokeWidth={2} fill="url(#gAtRisk)" />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card title="Recovery by strategy (gross recovered)">
          {strategies.isLoading ? (
            <Skeleton className="h-56" />
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={strategyData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="name" tick={{ fontSize: 10, fill: "#64748b" }} tickLine={false} interval={0} angle={-12} textAnchor="end" height={44} />
                <YAxis tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={false} tickFormatter={(v) => `₹${Math.round(v / 1000)}k`} />
                <Tooltip formatter={(v: number) => `₹${v.toLocaleString("en-IN")}`} contentStyle={{ borderRadius: 8, border: "1px solid #e2e8f0", fontSize: 12 }} />
                <Bar dataKey="recovered" radius={[4, 4, 0, 0]}>
                  {strategyData.map((_, i) => (
                    <Cell key={i} fill={STRATEGY_COLORS[i % STRATEGY_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card title="Incidents by failure reason">
          {failures.isLoading ? (
            <Skeleton className="h-56" />
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={failureData} dataKey="value" nameKey="name" innerRadius={45} outerRadius={75} paddingAngle={2}>
                  {failureData.map((_, i) => (
                    <Cell key={i} fill={FAILURE_COLORS[i % FAILURE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ borderRadius: 8, border: "1px solid #e2e8f0", fontSize: 12 }} />
                <Legend wrapperStyle={{ fontSize: 10 }} iconSize={8} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Card>
      </div>
    </div>
  );
}
