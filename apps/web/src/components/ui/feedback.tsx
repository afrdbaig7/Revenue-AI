"use client";

import { AlertTriangle, Check, CircleDollarSign, LoaderCircle } from "lucide-react";
import { Badge, Skeleton } from "./core";
import { classes } from "./styles";

export function ToneDot({ tone, label }: { tone: "green" | "amber" | "red" | "gray"; label: string }) {
  const map = { green: "bg-mint-500", amber: "bg-amber-500", red: "bg-danger-500", gray: "bg-slate-300" };
  return <span className="inline-flex items-center gap-1.5 text-xs text-slate-600"><span aria-hidden="true" className={classes("h-1.5 w-1.5 rounded-full", map[tone])} />{label}</span>;
}

export function SyntheticBanner() {
  return <div role="note" className="mb-4 flex items-start gap-2.5 rounded-[var(--radius-control)] border border-amber-200 bg-amber-50 px-3 py-2.5 text-xs leading-5 text-amber-800"><AlertTriangle aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0" /><span><strong>SIMULATED RESULTS — SYNTHETIC TEST-MODE DATA.</strong> All figures on this screen come from seeded synthetic data or Razorpay TEST MODE. No real transactions.</span></div>;
}

export function ModePill({ mode }: { mode: string }) {
  return <Badge tone={mode === "TEST" ? "amber" : "navy"} dot>{mode === "TEST" ? "Test mode" : mode}</Badge>;
}

export function FullPageLoader({ label = "Preparing RecoverAI" }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-6" role="status" aria-label={label}>
      <div className="w-full max-w-xs rounded-xl border border-slate-200 bg-white p-5 shadow-card">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 items-center justify-center rounded-[var(--radius-control)] bg-ink-950 text-white"><CircleDollarSign aria-hidden="true" className="h-4 w-4" /></span>
          <div className="min-w-0 flex-1"><div className="text-sm font-semibold text-ink-950">RecoverAI</div><div className="mt-1 text-xs text-slate-500">{label}</div></div>
          <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin text-brand-500" />
        </div>
        <div className="mt-5 space-y-2.5" aria-hidden="true"><Skeleton className="h-3 w-full" /><Skeleton className="h-3 w-4/5" /><Skeleton className="h-3 w-2/3" /></div>
      </div>
    </div>
  );
}

export function SuccessIcon() {
  return <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-mint-50 text-mint-600"><Check aria-hidden="true" className="h-3 w-3" /></span>;
}
