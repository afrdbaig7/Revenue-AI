import {
  CopyX,
  ClockAlert,
  ShieldBan,
  UserCheck,
} from "lucide-react";

const SAFETIES = [
  {
    icon: CopyX,
    title: "Duplicate Webhook Guard",
    scenario: "Razorpay retries a webhook 5 times due to transient network blips.",
    solution: "Idempotency hash table checks incoming event ID before triggering state mutations. Duplicates are logged and safely discarded with HTTP 200.",
    tag: "IDEMPOTENT INGESTION",
  },
  {
    icon: ClockAlert,
    title: "Late-Authorization Race Guard",
    scenario: "Customer completes payment through another tab while an automated recovery workflow is scheduled.",
    solution: "Payment state machine allows FAILED → CAPTURED transitions and sends an immediate cancellation signal to Temporal workers, halting scheduled actions.",
    tag: "RACE CONDITION PROOF",
  },
  {
    icon: ShieldBan,
    title: "Unsafe Action Hard-Stop",
    scenario: "AI proposes a 30% discount or immediate SMS at 2:00 AM on a high-value payment.",
    solution: "Deterministic Policy Engine halts the action: discounts are hard-capped to policy bounds (e.g. 10%) and blackout windows enforce polite contact hours.",
    tag: "STOPPING RULES",
  },
  {
    icon: UserCheck,
    title: "Human Approval Queue (HITL)",
    scenario: "High-value enterprise payment failure (> ₹50,000) or high-friction strategy suggested.",
    solution: "Incident is held in state AWAITING_HUMAN_APPROVAL. An operator or owner must explicitly approve or reject the action in the dashboard before execution.",
    tag: "DUAL CONTROL",
  },
];

export function SafetyGrid() {
  return (
    <section className="relative py-20 sm:py-24 bg-white">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <span className="text-xs font-bold uppercase tracking-[0.08em] text-brand-600">
            FINANCIAL SAFETY & FAILURE RESILIENCE
          </span>
          <h2 className="mt-2 text-3xl font-bold tracking-tight text-ink-950 sm:text-4xl">
            Never double-charge. Never spam. Never hallucinate.
          </h2>
          <p className="mt-3 text-sm text-slate-600">
            Four concrete failure modes in payment recovery and how RecoverAI mathematically prevents them.
          </p>
        </div>

        <div className="mt-14 grid grid-cols-1 gap-6 md:grid-cols-2">
          {SAFETIES.map((safety) => {
            const Icon = safety.icon;
            return (
              <div
                key={safety.title}
                className="relative rounded-2xl border border-slate-200 bg-slate-50/50 p-6 sm:p-7 transition-all hover:border-slate-300 hover:shadow-sm"
              >
                <div className="flex items-center justify-between border-b border-slate-200/80 pb-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-ink-950 text-white shadow-sm">
                    <Icon className="h-5 w-5" />
                  </div>
                  <span className="rounded-full bg-slate-200/80 px-2.5 py-0.5 font-mono text-[9px] font-bold text-slate-700 uppercase">
                    {safety.tag}
                  </span>
                </div>

                <div className="mt-4">
                  <h3 className="text-base font-bold text-ink-950">{safety.title}</h3>
                  <div className="mt-3 rounded-lg border border-slate-200/60 bg-white p-3 text-xs">
                    <span className="font-semibold text-danger-700">Failure Risk: </span>
                    <span className="text-slate-600">{safety.scenario}</span>
                  </div>
                  <div className="mt-2 rounded-lg border border-mint-200/60 bg-mint-50/40 p-3 text-xs">
                    <span className="font-semibold text-mint-800">RecoverAI Guard: </span>
                    <span className="text-mint-900">{safety.solution}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
