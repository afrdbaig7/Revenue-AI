import { TrendingUp, ShieldCheck, ArrowRight, Percent, IndianRupee } from "lucide-react";
import Link from "next/link";

export function MoneyRecoveredSection() {
  return (
    <section className="relative py-20 sm:py-24 border-t border-slate-200/80 bg-white">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-3xl text-center">
          <span className="text-xs font-bold uppercase tracking-[0.08em] text-brand-600">
            Realized Financial Impact
          </span>
          <h2 className="mt-2 text-3xl font-bold tracking-tight text-ink-950 sm:text-4xl">
            Turn failed transactions into settled revenue
          </h2>
          <p className="mt-3 text-sm text-slate-600">
            RecoverAI tracks every rupee of payment volume at risk, calculates net intervention costs, and proves incremental recovery rate.
          </p>
          <div className="mt-2 text-[10px] font-bold uppercase tracking-wider text-amber-600">
            [ SIMULATED / SYNTHETIC TEST-MODE DATA ]
          </div>
        </div>

        {/* Dashboard Metric Showcase Cards */}
        <div className="mt-14 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {/* Card 1: Dominant Incremental Revenue */}
          <div className="relative overflow-hidden rounded-2xl border-2 border-mint-500 bg-mint-50/30 p-6 shadow-sm sm:col-span-2 lg:col-span-1 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold uppercase tracking-wider text-mint-800">
                  Incremental Revenue
                </span>
                <span className="rounded bg-mint-100 px-2 py-0.5 text-[10px] font-bold text-mint-800">
                  DOMINANT METRIC
                </span>
              </div>
              <div className="mt-4 text-3xl sm:text-4xl font-extrabold tabular-nums tracking-tight text-mint-600">
                +₹6,67,200
              </div>
              <p className="mt-2 text-xs text-slate-600">
                Pure net revenue gained above standard automated retries on identical traffic.
              </p>
            </div>
            <div className="mt-6 flex items-center gap-1.5 text-xs font-bold text-mint-700">
              <TrendingUp className="h-4 w-4" />
              <span>+24.8% lift vs baseline</span>
            </div>
          </div>

          {/* Card 2: Total Revenue Recovered */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm flex flex-col justify-between">
            <div>
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                Total Revenue Recovered
              </span>
              <div className="mt-4 text-3xl font-bold tabular-nums tracking-tight text-ink-950">
                ₹18,42,500
              </div>
              <p className="mt-2 text-xs text-slate-500">
                Total volume successfully captured across 3,420 resolved recovery incidents.
              </p>
            </div>
            <div className="mt-6 text-xs text-slate-400 font-mono">
              Net of ₹14,200 dispatch costs
            </div>
          </div>

          {/* Card 3: Recovery Rate */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm flex flex-col justify-between">
            <div>
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                Overall Recovery Rate
              </span>
              <div className="mt-4 text-3xl font-bold tabular-nums tracking-tight text-brand-600">
                68.4%
              </div>
              <p className="mt-2 text-xs text-slate-500">
                Compared to standard 43.6% baseline on Indian card &amp; UPI failure distributions.
              </p>
            </div>
            <div className="mt-6 text-xs text-slate-400 font-mono">
              1.41 avg attempts / incident
            </div>
          </div>

          {/* Card 4: Revenue At Risk Monitored */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm flex flex-col justify-between">
            <div>
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                Revenue At Risk Detected
              </span>
              <div className="mt-4 text-3xl font-bold tabular-nums tracking-tight text-slate-800">
                ₹26,93,700
              </div>
              <p className="mt-2 text-xs text-slate-500">
                Captured across 5,000 synthetic test payment failure events.
              </p>
            </div>
            <div className="mt-6 flex items-center gap-1 text-xs text-slate-500">
              <ShieldCheck className="h-3.5 w-3.5 text-slate-400" />
              <span>382 policy blocks saved spam</span>
            </div>
          </div>
        </div>

        {/* Live Dashboard CTA Banner */}
        <div className="mt-8 rounded-2xl border border-slate-200 bg-slate-50/70 p-5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="flex h-3 w-3 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-xs font-semibold text-ink-950">
              Explore real-time data in the interactive merchant dashboard
            </span>
          </div>
          <Link
            href="/overview"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-brand-600 hover:text-brand-700"
          >
            Go to Overview Dashboard
            <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      </div>
    </section>
  );
}
