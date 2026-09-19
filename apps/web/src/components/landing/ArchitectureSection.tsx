import { Server, Database, Cpu, Radio, Network, GitPullRequest, Shield, Terminal } from "lucide-react";

export function ArchitectureSection() {
  return (
    <section id="architecture" className="relative scroll-mt-16 bg-slate-950 py-24 text-white">
      {/* Subtle top/bottom borders */}
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-3xl text-center">
          <div className="inline-flex items-center gap-2 rounded-full bg-slate-800 px-3 py-1 text-xs font-semibold text-slate-300 ring-1 ring-slate-700">
            <Terminal className="h-3.5 w-3.5 text-brand-400" />
            System Architecture & Topology
          </div>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-white sm:text-4xl">
            Designed for high-throughput, mission-critical payments
          </h2>
          <p className="mt-3 text-sm text-slate-400">
            Built as a resilient modular platform in Java 21 Spring Boot and Python FastAPI with event-driven durability.
          </p>

          {/* Architecture verification pills */}
          <div className="mt-6 flex flex-wrap items-center justify-center gap-2">
            {["IDEMPOTENT", "MULTI-TENANT", "OBSERVABLE", "AUDITABLE", "EVENT-DRIVEN", "DURABLE"].map((pill) => (
              <span
                key={pill}
                className="rounded-full bg-slate-900 px-3 py-1 font-mono text-[10px] font-bold tracking-wider text-brand-300 ring-1 ring-brand-500/20"
              >
                {pill}
              </span>
            ))}
          </div>
        </div>

        {/* System Topology Diagram Card */}
        <div className="mt-16 rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-2xl backdrop-blur-sm sm:p-10">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-4">
            {/* Layer 1: Ingress & Frontend */}
            <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-brand-400">
                <Network className="h-4 w-4" />
                <span>Ingress & Web UI</span>
              </div>
              <div className="mt-4 space-y-3 text-xs">
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Next.js 15 Web Dashboard</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Port 3000 · React 19 · In-memory JWT · TanStack Query · Recharts
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Webhook Ingestion Gateway</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    HMAC-SHA256 signature verification · Raw payload inbox
                  </div>
                </div>
              </div>
            </div>

            {/* Layer 2: Core Control Plane */}
            <div className="rounded-2xl border border-brand-500/30 bg-slate-950/80 p-5 ring-1 ring-brand-500/10">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-200">
                <Server className="h-4 w-4 text-brand-400" />
                <span>Spring Boot API</span>
              </div>
              <div className="mt-4 space-y-3 text-xs">
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Payment & Incident FSM</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    18-state recovery state machine · 7-state payment lifecycle
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">EV Decision & Policy Engine</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Deterministic mathematical scoring + stopping rules
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Transactional Outbox</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Atomic DB commit + background event dispatcher
                  </div>
                </div>
              </div>
            </div>

            {/* Layer 3: Event Stream & Workflows */}
            <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-emerald-400">
                <Radio className="h-4 w-4" />
                <span>Async & Temporal</span>
              </div>
              <div className="mt-4 space-y-3 text-xs">
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Redpanda Event Streaming</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Kafka-compatible pub/sub · Partitioned by org/merchant ID
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Temporal Workflow Engine</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Durable recovery timers · Cancellation signals on late auth
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Redis 7 State Cache</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Distributed locking & sliding-window rate limiters
                  </div>
                </div>
              </div>
            </div>

            {/* Layer 4: AI Microservice & Adapters */}
            <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-sky-400">
                <Cpu className="h-4 w-4" />
                <span>AI Service & Gateway</span>
              </div>
              <div className="mt-4 space-y-3 text-xs">
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">FastAPI Advisory Service</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Port 8100 · OpenAI / Gemini / Groq / Fallback · Pydantic v2
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">Razorpay Payment Adapter</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Payment links · Smart retries · Simulated test mode
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-900 p-3">
                  <div className="font-semibold text-slate-200">PostgreSQL 16 Storage</div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Flyway versioned migrations · JSONB event schemas
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Bottom architectural note */}
          <div className="mt-8 flex flex-wrap items-center justify-between gap-4 border-t border-slate-800 pt-6 text-xs text-slate-400">
            <div>
              <span className="font-semibold text-slate-200">Zero Single Point of Failure: </span>
              <span>If the AI microservice fails or times out, the system automatically degrades to pure deterministic heuristics.</span>
            </div>
            <div className="font-mono text-[11px] text-brand-300">
              Spring Boot 3.5.7 · Java 21 Virtual Threads · Python 3.12
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
