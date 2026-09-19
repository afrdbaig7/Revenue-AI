import {
  Layers,
  GitFork,
  ShieldAlert,
  FileCheck,
  Send,
  Zap,
} from "lucide-react";

const PROOFS = [
  {
    icon: Layers,
    title: "12 Recovery Strategies",
    description:
      "From delayed intelligent retries and UPI collect links to SMS, WhatsApp, and promise-to-pay negotiation.",
    tag: "CATALOG-DRIVEN",
  },
  {
    icon: GitFork,
    title: "18 Incident States",
    description:
      "Strict finite state machine governing every transition from failure detection to late-authorization reconciliation.",
    tag: "DETERMINISTIC FSM",
  },
  {
    icon: Zap,
    title: "Transactional Outbox",
    description:
      "Atomic database mutations + Redpanda (Kafka) event publishing guarantees zero lost payment failure events.",
    tag: "AT-LEAST-ONCE",
  },
  {
    icon: ShieldAlert,
    title: "Human-in-the-Loop Gates",
    description:
      "High-friction or high-discount actions automatically hold in an operator queue for dual-control authorization.",
    tag: "RISK-CONTROLLED",
  },
  {
    icon: FileCheck,
    title: "Immutable Audit Ledger",
    description:
      "Append-only log recording every diagnosis, policy evaluation, state change, and trace correlation ID.",
    tag: "COMPLIANCE-READY",
  },
  {
    icon: Send,
    title: "Temporal Durable Workflows",
    description:
      "Resilient scheduling and retry backoffs that survive application restarts, container crashes, and network partitions.",
    tag: "FAULT-TOLERANT",
  },
];

export function ProofGrid() {
  return (
    <section className="relative border-y border-slate-200/80 bg-slate-50/50 py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-xs font-bold uppercase tracking-[0.08em] text-brand-600">
            Engineered For Fintech Reliability
          </h2>
          <p className="mt-2 text-2xl font-bold tracking-tight text-ink-950 sm:text-3xl">
            Built on verified engineering principles, not black-box promises.
          </p>
          <p className="mt-3 text-sm text-slate-600">
            Every component in RecoverAI is backed by verifiable state machines, transactional outbox guarantees, and deterministic policy boundaries.
          </p>
        </div>

        <div className="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {PROOFS.map((proof) => {
            const Icon = proof.icon;
            return (
              <div
                key={proof.title}
                className="group relative rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-slate-300 hover:shadow-md"
              >
                <div className="flex items-center justify-between">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600 ring-1 ring-brand-100">
                    <Icon className="h-5 w-5" />
                  </div>
                  <span className="rounded-full bg-slate-100 px-2.5 py-0.5 font-mono text-[9px] font-bold tracking-wider text-slate-600 uppercase">
                    {proof.tag}
                  </span>
                </div>
                <h3 className="mt-4 text-base font-semibold text-ink-950">
                  {proof.title}
                </h3>
                <p className="mt-2 text-xs leading-5 text-slate-600">
                  {proof.description}
                </p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
