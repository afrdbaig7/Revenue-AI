"use client";

import { CheckCircle2, AlertTriangle, ShieldCheck, Sparkles, TrendingUp } from "lucide-react";

export function HeroVisual() {
  return (
    <div className="relative mx-auto w-full max-w-lg lg:max-w-none">
      {/* Glow highlight behind card */}
      <div className="absolute -inset-1 rounded-2xl bg-gradient-to-tr from-[#635bff]/20 via-[#00d4b2]/20 to-[#ff62a5]/20 opacity-70 blur-xl transition-all" />

      {/* Main card */}
      <div className="relative rounded-2xl border border-slate-200/80 bg-white p-5 shadow-2xl backdrop-blur-sm sm:p-6">
        {/* Mockup Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2">
            <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-50" />
            <span className="text-xs font-semibold text-ink-950">RecoverAI Live Pipeline</span>
          </div>
          <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-amber-700 ring-1 ring-inset ring-amber-200">
            SIMULATED DEMO
          </span>
        </div>

        {/* Real-time KPI Stats row */}
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
          <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              Recovered Revenue
            </div>
            <div className="mt-1 text-xl font-bold tabular-nums text-mint-600">
              ₹18.42L
            </div>
            <div className="mt-0.5 flex items-center gap-1 text-[11px] font-medium text-mint-600">
              <TrendingUp className="h-3 w-3" />
              <span>+24.8% lift</span>
            </div>
          </div>

          <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              Active Incidents
            </div>
            <div className="mt-1 text-xl font-bold tabular-nums text-ink-950">
              42
            </div>
            <div className="mt-0.5 text-[11px] text-slate-500">
              12 in diagnosis
            </div>
          </div>

          <div className="col-span-2 rounded-xl border border-slate-100 bg-slate-50/70 p-3 sm:col-span-1">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              Policy Guardrails
            </div>
            <div className="mt-1 text-xl font-bold tabular-nums text-brand-600">
              100%
            </div>
            <div className="mt-0.5 text-[11px] text-slate-500">
              Deterministic safety
            </div>
          </div>
        </div>

        {/* Live Recovery Trace Pipeline */}
        <div className="mt-5 rounded-xl border border-slate-100 bg-slate-50/40 p-4">
          <div className="flex items-center justify-between text-[11px] font-semibold text-slate-500">
            <span>LIVE INCIDENT #INC-8942</span>
            <span className="font-mono text-[10px] text-slate-400">10:14:02 IST</span>
          </div>

          <div className="mt-3 space-y-2.5">
            {/* Step 1: Failed */}
            <div className="flex items-start gap-2.5 text-xs">
              <div className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-danger-50 text-danger-600 ring-1 ring-danger-200">
                <AlertTriangle className="h-3 w-3" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="font-semibold text-slate-800">
                  Payment Failed: ₹4,299 (HDFC Gateway Decline)
                </div>
                <div className="text-[11px] text-slate-500">
                  Webhook received · Error: <code className="font-mono text-slate-600">CARD_EXPIRED</code>
                </div>
              </div>
            </div>

            {/* Step 2: AI Diagnosis & EV Ranking */}
            <div className="flex items-start gap-2.5 text-xs">
              <div className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-600 ring-1 ring-brand-200">
                <Sparkles className="h-3 w-3" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5 font-semibold text-slate-800">
                  <span>AI Diagnosis: Card Expiry (95% confidence)</span>
                  <span className="rounded bg-brand-50 px-1.5 py-0.2 text-[10px] font-bold text-brand-700">
                    ADVISORY
                  </span>
                </div>
                <div className="text-[11px] text-slate-500">
                  Candidate strategy ranked #1: <span className="font-semibold text-brand-700">PAYMENT_LINK</span> (EV: ₹2,235)
                </div>
              </div>
            </div>

            {/* Step 3: Policy Authorization */}
            <div className="flex items-start gap-2.5 text-xs">
              <div className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-sky-50 text-sky-600 ring-1 ring-sky-200">
                <ShieldCheck className="h-3 w-3" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="font-semibold text-slate-800">
                  Deterministic Policy Check: ALLOWED
                </div>
                <div className="text-[11px] text-slate-500">
                  Retry limit 0/3 · Cooldown passed · Approval threshold passed
                </div>
              </div>
            </div>

            {/* Step 4: Recovered */}
            <div className="flex items-start gap-2.5 text-xs">
              <div className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-mint-50 text-mint-600 ring-1 ring-mint-400/30">
                <CheckCircle2 className="h-3 w-3" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="font-semibold text-mint-700">
                  Payment Link Completed: ₹4,299 Recovered
                </div>
                <div className="text-[11px] text-slate-500">
                  Razorpay webhook verified · Audit ledger updated
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Bounded AI Pill Banner */}
        <div className="mt-4 flex items-center justify-between rounded-lg bg-ink-950 px-3 py-2 text-[11px] text-slate-200">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-brand-400" />
            <span>AI recommends. Deterministic policy executes.</span>
          </div>
          <span className="font-mono text-[10px] text-slate-400">Zero LLM write access</span>
        </div>
      </div>
    </div>
  );
}
