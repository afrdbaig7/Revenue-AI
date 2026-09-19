import { Sparkles, ShieldCheck, Lock, ArrowRight, Ban, CheckCircle2 } from "lucide-react";

export function TrustBoundarySection() {
  return (
    <section id="safety" className="relative scroll-mt-16 border-y border-slate-200/80 bg-slate-900 py-20 text-white sm:py-28">
      {/* Background glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[400px] w-[800px] bg-brand-600/10 blur-[130px] -z-0 pointer-events-none" />

      <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-3xl text-center">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-500/10 px-3 py-1 text-xs font-semibold text-brand-300 ring-1 ring-brand-400/30">
            <Lock className="h-3.5 w-3.5" />
            Bounded AI Architecture
          </span>
          <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl text-white">
            AI can recommend an action. <br className="hidden sm:inline" />
            It cannot move money.
          </h2>
          <p className="mt-4 text-sm text-slate-300 leading-relaxed max-w-2xl mx-auto">
            We separate probabilistic LLM intelligence from financial execution. The AI layer produces structured advisory recommendations. Every single action must pass deterministic policy authorization before a byte is dispatched.
          </p>
        </div>

        {/* The Split Architecture with Visible Trust Boundary */}
        <div className="mt-16 grid grid-cols-1 gap-8 lg:grid-cols-11 lg:items-center">
          {/* Left: AI Advisory Layer (Col 1-5) */}
          <div className="rounded-2xl border border-brand-500/20 bg-slate-800/80 p-6 shadow-xl lg:col-span-5">
            <div className="flex items-center justify-between border-b border-slate-700/60 pb-3">
              <div className="flex items-center gap-2 text-brand-300">
                <Sparkles className="h-4 w-4" />
                <span className="text-xs font-bold uppercase tracking-wider">AI Advisory Layer (Probabilistic)</span>
              </div>
              <span className="rounded bg-brand-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-brand-300">
                READ-ONLY
              </span>
            </div>

            <div className="mt-4 space-y-3 text-xs">
              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Root Cause Diagnosis</div>
                <div className="mt-1 text-slate-400">
                  Maps raw payment gateway codes + metadata to 12 normalized failure categories with evidence strings.
                </div>
              </div>

              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Expected Value (EV) Strategy Ranking</div>
                <div className="mt-1 text-slate-400">
                  Scores 12 recovery strategies based on customer history, transaction amount, and failure category.
                </div>
              </div>

              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Conversational Promise Extraction</div>
                <div className="mt-1 text-slate-400">
                  Parses customer WhatsApp/SMS responses (e.g. &quot;I will pay Friday afternoon&quot;) into structured date commitments.
                </div>
              </div>

              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Strict Pydantic Schema Guards</div>
                <div className="mt-1 text-slate-400">
                  Any LLM output failing schema bounds immediately degrades to the deterministic fallback engine.
                </div>
              </div>
            </div>

            <div className="mt-4 flex items-center gap-2 rounded-lg bg-brand-950/40 px-3 py-2 text-[11px] text-brand-200 border border-brand-500/20">
              <CheckCircle2 className="h-3.5 w-3.5 text-brand-400 shrink-0" />
              <span>Zero direct database write permissions. Zero gateway execution access.</span>
            </div>
          </div>

          {/* Center: THE TRUST BOUNDARY (Col 6) */}
          <div className="flex flex-col items-center justify-center lg:col-span-1 py-4 lg:py-0">
            <div className="h-12 w-px bg-gradient-to-b from-brand-500 to-emerald-500 hidden lg:block" />
            <div className="my-2 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-950 ring-2 ring-brand-400/40 text-brand-300 shadow-lg">
              <Lock className="h-4 w-4" />
            </div>
            <span className="font-mono text-[9px] font-extrabold uppercase tracking-widest text-slate-400 text-center mt-1">
              TRUST<br className="hidden lg:inline" />BOUNDARY
            </span>
            <div className="h-12 w-px bg-gradient-to-b from-emerald-500 to-brand-500 hidden lg:block" />
          </div>

          {/* Right: Deterministic Policy Engine (Col 7-11) */}
          <div className="rounded-2xl border border-emerald-500/20 bg-slate-800/80 p-6 shadow-xl lg:col-span-5">
            <div className="flex items-center justify-between border-b border-slate-700/60 pb-3">
              <div className="flex items-center gap-2 text-emerald-300">
                <ShieldCheck className="h-4 w-4" />
                <span className="text-xs font-bold uppercase tracking-wider">Deterministic Policy Engine</span>
              </div>
              <span className="rounded bg-emerald-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-emerald-300">
                AUTHORITY GATE
              </span>
            </div>

            <div className="mt-4 space-y-3 text-xs">
              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Merchant Safety Limits & Caps</div>
                <div className="mt-1 text-slate-400">
                  Enforces max retries (e.g. max 3), minimum recoverable amount, and strict discount ceilings.
                </div>
              </div>

              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Customer Cooldown & Opt-Out Tracking</div>
                <div className="mt-1 text-slate-400">
                  Prevents spam by enforcing channel cooldown timers (e.g. 24h between SMS) and opt-out registries.
                </div>
              </div>

              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Operator Approval Thresholds</div>
                <div className="mt-1 text-slate-400">
                  High-value transactions (e.g. &gt; ₹50,000) or high friction strategies require human sign-off.
                </div>
              </div>

              <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
                <div className="font-semibold text-slate-200">Idempotency & Race-Condition Guards</div>
                <div className="mt-1 text-slate-400">
                  Prevents double-charging when late authorizations arrive via Temporal cancellation signals.
                </div>
              </div>
            </div>

            <div className="mt-4 flex items-center gap-2 rounded-lg bg-emerald-950/40 px-3 py-2 text-[11px] text-emerald-200 border border-emerald-500/20">
              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>Full cryptographic audit trail on every ALLOW / BLOCK decision.</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
