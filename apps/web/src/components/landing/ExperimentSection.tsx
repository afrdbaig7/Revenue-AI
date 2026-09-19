import { ArrowUpRight, TrendingUp, CheckCircle, ShieldAlert, Users, Layers } from "lucide-react";

export function ExperimentSection() {
  return (
    <section id="experiments" className="relative scroll-mt-16 py-20 sm:py-24 bg-white">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-3xl text-center">
          <div className="inline-flex items-center gap-2 rounded-full bg-mint-50 px-3 py-1 text-xs font-semibold text-mint-700 ring-1 ring-mint-200">
            <TrendingUp className="h-3.5 w-3.5" />
            Empirical A/B Benchmarking
          </div>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-ink-950 sm:text-4xl">
            Measure incremental recovered revenue. <br className="hidden sm:inline" />
            Not just gross recovery.
          </h2>
          <p className="mt-3 text-sm text-slate-600">
            Does AI recovery actually outperform standard 3-retry rules? RecoverAI runs continuous A/B experiments to prove incremental lift with 95% confidence intervals.
          </p>
          <div className="mt-2 text-[11px] font-bold uppercase tracking-wider text-amber-600">
            [ SIMULATED TEST-MODE EXPERIMENT DATA · 10,000 INCIDENTS FIXTURE ]
          </div>
        </div>

        {/* Experiment Results Comparison Card */}
        <div className="mt-14 rounded-2xl border border-slate-200 bg-slate-50/50 p-6 shadow-sm sm:p-8">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {/* Dominant KPI: Incremental Revenue Lift */}
            <div className="rounded-2xl border border-mint-200 bg-white p-6 shadow-sm flex flex-col justify-between">
              <div>
                <span className="text-[11px] font-bold uppercase tracking-wider text-mint-700">
                  Incremental Revenue Recovered
                </span>
                <div className="mt-2 text-4xl font-extrabold tabular-nums tracking-tight text-mint-600">
                  +₹6.67L
                </div>
                <p className="mt-2 text-xs text-slate-600 leading-relaxed">
                  Net additional revenue recovered above what the control strategy achieved on the identical incident population.
                </p>
              </div>

              <div className="mt-6 border-t border-slate-100 pt-4">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-500">Recovery Rate Lift</span>
                  <span className="font-bold text-mint-600 tabular-nums">+24.8% (p &lt; 0.001)</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs">
                  <span className="text-slate-500">95% Confidence Interval</span>
                  <span className="font-mono text-slate-700">[+21.4%, +28.2%]</span>
                </div>
              </div>
            </div>

            {/* Arm 1: Control (Standard 3-Retry) */}
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <span className="font-semibold text-xs text-slate-600 uppercase tracking-wider">
                  Control Arm (Fixed Rules)
                </span>
                <span className="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-600">
                  STATIC 3-RETRY
                </span>
              </div>

              <div className="mt-4 space-y-4">
                <div>
                  <div className="text-xs text-slate-500">Recovery Rate</div>
                  <div className="text-2xl font-bold tabular-nums text-slate-800">43.6%</div>
                  <div className="text-[11px] text-slate-400">2,180 of 5,000 incidents</div>
                </div>

                <div className="grid grid-cols-2 gap-3 border-t border-slate-100 pt-3 text-xs">
                  <div>
                    <div className="text-slate-500">Net Recovered</div>
                    <div className="font-semibold tabular-nums text-slate-800">₹11.75L</div>
                  </div>
                  <div>
                    <div className="text-slate-500">Attempts / Incident</div>
                    <div className="font-semibold tabular-nums text-slate-800">2.84</div>
                  </div>
                </div>

                <div className="rounded-lg bg-slate-50 p-2.5 text-[11px] text-slate-500">
                  Blind retries without root cause diagnosis led to customer friction on invalid cards.
                </div>
              </div>
            </div>

            {/* Arm 2: Treatment (RecoverAI EV Engine) */}
            <div className="rounded-2xl border border-brand-200 bg-brand-50/20 p-6 shadow-sm ring-1 ring-brand-500/10">
              <div className="flex items-center justify-between border-b border-brand-100 pb-3">
                <span className="font-semibold text-xs text-brand-700 uppercase tracking-wider">
                  Treatment Arm (RecoverAI)
                </span>
                <span className="rounded bg-brand-100 px-2 py-0.5 text-[10px] font-bold text-brand-700">
                  ADAPTIVE EV ENGINE
                </span>
              </div>

              <div className="mt-4 space-y-4">
                <div>
                  <div className="text-xs text-brand-700 font-medium">Recovery Rate</div>
                  <div className="text-2xl font-bold tabular-nums text-brand-600">68.4%</div>
                  <div className="text-[11px] text-brand-700/80">3,420 of 5,000 incidents</div>
                </div>

                <div className="grid grid-cols-2 gap-3 border-t border-brand-100 pt-3 text-xs">
                  <div>
                    <div className="text-slate-500">Net Recovered</div>
                    <div className="font-bold tabular-nums text-mint-600">₹18.42L</div>
                  </div>
                  <div>
                    <div className="text-slate-500">Attempts / Incident</div>
                    <div className="font-semibold tabular-nums text-slate-800">1.41 (fewer spam)</div>
                  </div>
                </div>

                <div className="rounded-lg bg-brand-50/80 p-2.5 text-[11px] text-brand-800">
                  Switched payment methods dynamically and timed retries based on bank uptime windows.
                </div>
              </div>
            </div>
          </div>

          {/* Efficiency Footnote */}
          <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white px-5 py-3.5 text-xs text-slate-600">
            <div className="flex items-center gap-2">
              <CheckCircle className="h-4 w-4 text-mint-600 shrink-0" />
              <span>
                <strong>2,140 unnecessary customer contacts saved</strong> by suppressing retries on blocked cards.
              </span>
            </div>
            <span className="font-mono text-[11px] text-slate-400">
              Methodology: Deterministic Hash Modulo Split
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
