"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { formatINR } from "@/lib/format";
import { PageHeader, Card, Skeleton, EmptyState, SyntheticBanner } from "@/components/ui";

const STRATEGY_GLOSSARY: Record<string, string> = {
  WAIT_FOR_PROVIDER_RETRY: "Let the payment provider run its own retry cycle; monitor only.",
  DELAYED_RETRY: "Re-attempt the payment at a scheduled, customer-friendly hour.",
  PAYMENT_LINK: "Send a secure payment link the customer completes on their own time.",
  ALTERNATE_PAYMENT_METHOD: "Encourage a different payment method.",
  UPI_RECOVERY: "Recover via a UPI payment link or intent.",
  EMAIL_NUDGE: "One polite, bounded email reminder.",
  WHATSAPP_NUDGE: "A WhatsApp reminder with a secure payment link.",
  SMS_NUDGE: "A short SMS reminder with a secure payment link.",
  BOUNDED_DISCOUNT: "Policy-bounded discount for higher-value payments.",
  PROMISE_TO_PAY: "Capture an explicit customer promise and follow up durably.",
  MANUAL_ESCALATION: "Hand the incident to a human operator.",
  NO_ACTION: "Incident is not economically recoverable.",
};

export default function StrategiesPage() {
  const strategies = useQuery({
    queryKey: ["strategies"],
    queryFn: () => api.get<Array<{ strategy: string; uses: number; successes: number; recoveredMinor: number }>>("/dashboard/strategies"),
  });

  const rows = strategies.data ?? [];
  const totalUses = rows.reduce((acc, r) => acc + r.uses, 0);

  return (
    <div>
      <PageHeader
        title="Recovery Strategies"
        subtitle="Strategy performance across all incidents. Every strategy is policy-gated; AI can only rank among them."
      />
      <SyntheticBanner />

      <Card className="overflow-hidden p-0">
        {strategies.isLoading ? (
          <div className="space-y-2 p-4">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-10" />)}</div>
        ) : rows.length === 0 ? (
          <div className="p-4"><EmptyState title="No strategy data yet" /></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table w-full">
              <thead>
                <tr>
                  <th>Strategy</th>
                  <th>Uses</th>
                  <th>Share</th>
                  <th>Success rate</th>
                  <th>Gross recovered</th>
                  <th>Notes</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.strategy}>
                    <td className="font-semibold text-slate-800">{r.strategy.replaceAll("_", " ")}</td>
                    <td className="tabular-nums">{r.uses}</td>
                    <td>
                      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-slate-100">
                        <div className="h-full rounded-full bg-brand-500" style={{ width: `${totalUses ? (r.uses / totalUses) * 100 : 0}%` }} />
                      </div>
                    </td>
                    <td className="tabular-nums">{r.uses ? `${((r.successes / r.uses) * 100).toFixed(0)}%` : "—"}</td>
                    <td className="font-semibold tabular-nums text-emerald-600">{formatINR(r.recoveredMinor, { noDecimals: true })}</td>
                    <td className="max-w-xs text-[11px] text-slate-500">{STRATEGY_GLOSSARY[r.strategy] ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <Card title="How selection works">
          <ol className="space-y-2.5 text-[13px] text-slate-600">
            <li className="flex gap-2"><span className="font-mono text-[11px] font-semibold text-brand-600">1.</span> Deterministic eligibility: which strategies may apply to this failure category, method, and incident type.</li>
            <li className="flex gap-2"><span className="font-mono text-[11px] font-semibold text-brand-600">2.</span> Expected value: EV = P(recovery) × net amount − intervention − discount − friction − risk.</li>
            <li className="flex gap-2"><span className="font-mono text-[11px] font-semibold text-brand-600">3.</span> AI may re-rank among the eligible set (validated, auditable).</li>
            <li className="flex gap-2"><span className="font-mono text-[11px] font-semibold text-brand-600">4.</span> Policy engine authorizes — or blocks — before anything executes.</li>
          </ol>
        </Card>
        <Card title="Safety model">
          <ul className="space-y-2 text-[13px] text-slate-600">
            <li>• Retry, contact, and discount limits are deterministic — AI cannot override them.</li>
            <li>• Every execution re-reconciles the payment first (late-authorization protection).</li>
            <li>• Idempotency keys make every action repeat-safe.</li>
            <li>• High-value, discount, and low-confidence proposals require human approval.</li>
          </ul>
        </Card>
      </div>
    </div>
  );
}
