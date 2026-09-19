import {
  AlertCircle,
  Search,
  Sparkles,
  Calculator,
  ShieldCheck,
  Zap,
  RefreshCw,
  BarChart3,
} from "lucide-react";

const STEPS = [
  {
    step: "01",
    name: "Detect",
    icon: Search,
    title: "Failure Ingestion",
    desc: "Razorpay webhooks & checkout drops are verified with HMAC-SHA256 and recorded atomically via Transactional Outbox.",
  },
  {
    step: "02",
    name: "Diagnose",
    icon: Sparkles,
    title: "Root Cause Classification",
    desc: "Advisory AI maps failure codes across 12 distinct root categories (insufficient funds, bank degradation, expired credentials, etc.).",
  },
  {
    step: "03",
    name: "Decide",
    icon: Calculator,
    title: "Expected Value Ranking",
    desc: "Mathematical scoring computes Expected Value: EV = P(recovery) × Amount − Costs − Friction. Top strategy is proposed.",
  },
  {
    step: "04",
    name: "Authorize",
    icon: ShieldCheck,
    title: "Deterministic Guardrails",
    desc: "Hard merchant policies verify retry limits, customer contact cooldowns, discount bounds, and approval thresholds.",
  },
  {
    step: "05",
    name: "Recover",
    icon: Zap,
    title: "Bounded Execution",
    desc: "Temporal durable workflows schedule and execute actions across SMS, WhatsApp, payment links, or automated smart retries.",
  },
  {
    step: "06",
    name: "Reconcile",
    icon: RefreshCw,
    title: "State Reconciliation",
    desc: "Late-authorization detection and idempotency keys guarantee customers are never double-charged or spammed.",
  },
  {
    step: "07",
    name: "Measure",
    icon: BarChart3,
    title: "Statistical Evaluation",
    desc: "A/B testing evaluates recovery lift and net recovered revenue against a fixed baseline with empirical confidence intervals.",
  },
];

export function RecoveryLoop() {
  return (
    <section id="how-it-works" className="relative scroll-mt-16 py-20 sm:py-24">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-xs font-bold uppercase tracking-[0.08em] text-brand-600">
            End-to-End Recovery Lifecycle
          </h2>
          <p className="mt-2 text-3xl font-bold tracking-tight text-ink-950 sm:text-4xl">
            How RecoverAI saves failed revenue
          </p>
          <p className="mt-3 text-sm text-slate-600">
            From the millisecond a payment failure is detected to the final settlement in your Razorpay merchant balance.
          </p>
        </div>

        {/* Visual Pipeline Grid */}
        <div className="mt-16 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((s, idx) => {
            const Icon = s.icon;
            return (
              <div
                key={s.step}
                className="relative flex flex-col rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-brand-200 hover:shadow-md"
              >
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <span className="font-mono text-xs font-bold text-brand-600">
                    PHASE {s.step}
                  </span>
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-50 text-slate-700 ring-1 ring-slate-100">
                    <Icon className="h-4 w-4 text-brand-600" />
                  </div>
                </div>

                <div className="mt-4 flex-1">
                  <h3 className="text-sm font-bold text-ink-950">{s.title}</h3>
                  <p className="mt-2 text-xs leading-5 text-slate-600">{s.desc}</p>
                </div>
              </div>
            );
          })}

          {/* Final Summary Card */}
          <div className="flex flex-col justify-between rounded-2xl border border-mint-400/30 bg-mint-50/50 p-6 shadow-sm">
            <div className="flex items-center justify-between border-b border-mint-200/60 pb-3">
              <span className="font-mono text-xs font-bold text-mint-700">
                OUTCOME
              </span>
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-mint-100 text-mint-700">
                <AlertCircle className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-4">
              <h3 className="text-sm font-bold text-mint-900">Revenue Recovered</h3>
              <p className="mt-2 text-xs leading-5 text-mint-800">
                Settled to merchant account, customer relationship protected, and full audit trace permanently logged.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
