import {
  Clock,
  AlertTriangle,
  Sparkles,
  Calculator,
  ShieldCheck,
  Zap,
  CheckCircle2,
} from "lucide-react";

export function JourneySection() {
  return (
    <section className="relative py-20 sm:py-24 border-t border-slate-200/80 bg-slate-50/40">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <span className="text-xs font-bold uppercase tracking-[0.08em] text-brand-600">
            Real-world Recovery Walkthrough
          </span>
          <h2 className="mt-2 text-3xl font-bold tracking-tight text-ink-950 sm:text-4xl">
            A single payment&apos;s journey to settlement
          </h2>
          <p className="mt-3 text-sm text-slate-600">
            Follow how RecoverAI handles Incident <code className="font-mono text-xs font-semibold text-slate-800">#INC-8942</code> (₹4,299 transaction) in milliseconds.
          </p>
          <div className="mt-2 text-[10px] font-bold uppercase tracking-wider text-amber-600">
            [ SIMULATED DEMO WALKTHROUGH ]
          </div>
        </div>

        {/* Timeline Trace Box */}
        <div className="mt-14 max-w-4xl mx-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <div className="flex items-center gap-3">
              <span className="font-mono text-xs font-bold text-slate-500">
                INCIDENT ID: 8942-8f92-482a
              </span>
              <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[10px] font-semibold text-slate-700">
                MERCHANT: Demo Store Pvt Ltd
              </span>
            </div>
            <span className="rounded-full bg-mint-50 px-2.5 py-0.5 text-xs font-bold text-mint-700 ring-1 ring-mint-200">
              STATUS: RECOVERED
            </span>
          </div>

          <div className="mt-8 space-y-6">
            {/* 1. Failure Event */}
            <div className="relative flex items-start gap-4 pl-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-danger-50 text-danger-600 ring-4 ring-white shadow-sm">
                <AlertTriangle className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1 pt-0.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-bold text-ink-950">
                    Payment Failed: ₹4,299.00
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">10:14:02.102 IST</span>
                </div>
                <p className="mt-1 text-xs text-slate-600">
                  Razorpay webhook received: <code className="font-mono text-[11px] text-slate-700">BAD_REQUEST_ERROR / CARD_EXPIRED</code>. Verified HMAC signature and recorded raw payload in inbox table.
                </p>
              </div>
            </div>

            {/* 2. AI Diagnosis */}
            <div className="relative flex items-start gap-4 pl-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-600 ring-4 ring-white shadow-sm">
                <Sparkles className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1 pt-0.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-bold text-ink-950">
                    AI Diagnosis Classifier Evaluated
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">10:14:02.340 IST</span>
                </div>
                <p className="mt-1 text-xs text-slate-600">
                  Classified root cause as <span className="font-semibold text-brand-700">CARD_EXPIRED</span> with 95% confidence. Hard retries suppressed because card expiry requires new customer credentials.
                </p>
              </div>
            </div>

            {/* 3. Expected Value Ranking */}
            <div className="relative flex items-start gap-4 pl-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 ring-4 ring-white shadow-sm">
                <Calculator className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1 pt-0.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-bold text-ink-950">
                    EV Decision Engine Scored 12 Candidate Strategies
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">10:14:02.390 IST</span>
                </div>
                <div className="mt-2 rounded-lg bg-slate-50 p-3 font-mono text-[11px] text-slate-700 border border-slate-100">
                  Top Choice: PAYMENT_LINK (P=0.52, EV=₹2,235.48) &gt; WHATSAPP_NUDGE (P=0.24, EV=₹1,031.76) &gt; DELAYED_RETRY (P=0.18, EV=₹773.82)
                </div>
              </div>
            </div>

            {/* 4. Policy Check */}
            <div className="relative flex items-start gap-4 pl-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-sky-50 text-sky-600 ring-4 ring-white shadow-sm">
                <ShieldCheck className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1 pt-0.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-bold text-ink-950">
                    Policy Engine Verified Guardrails: ALLOWED
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">10:14:02.410 IST</span>
                </div>
                <p className="mt-1 text-xs text-slate-600">
                  Merchant policy check passed: Cooldown elapsed (0 previous attempts today), within business hours, amount ₹4,299 &lt; ₹50,000 threshold (no manual operator approval required).
                </p>
              </div>
            </div>

            {/* 5. Temporal Dispatch */}
            <div className="relative flex items-start gap-4 pl-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-600 ring-4 ring-white shadow-sm">
                <Zap className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1 pt-0.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-bold text-ink-950">
                    Recovery Action Dispatched via Temporal
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">10:14:03.015 IST</span>
                </div>
                <p className="mt-1 text-xs text-slate-600">
                  Generated Razorpay payment link and dispatched automated recovery message to customer&apos;s preferred channel.
                </p>
              </div>
            </div>

            {/* 6. Settlement */}
            <div className="relative flex items-start gap-4 pl-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-mint-50 text-mint-600 ring-4 ring-white shadow-sm">
                <CheckCircle2 className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1 pt-0.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-bold text-mint-700">
                    Customer Paid via UPI: ₹4,299.00 Recovered
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">10:32:15.820 IST</span>
                </div>
                <p className="mt-1 text-xs text-slate-600">
                  Razorpay webhook <code className="font-mono text-[11px] text-slate-700">payment.captured</code> arrived. Incident transitioned to terminal state <span className="font-semibold text-mint-700">RECOVERED</span>. Audit entry immutably committed.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
