import Link from "next/link";
import { CircleDollarSign, ArrowRight, ShieldCheck, Github } from "lucide-react";

export function LandingFooter() {
  return (
    <footer className="border-t border-slate-200 bg-white">
      {/* Pre-footer Final CTA */}
      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 sm:py-20 lg:px-8">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-tr from-ink-950 via-slate-900 to-brand-950 p-8 text-white sm:p-12 lg:p-16">
          <div className="relative z-10 max-w-2xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-500/20 px-3 py-1 text-xs font-semibold text-brand-300 ring-1 ring-brand-400/30">
              <ShieldCheck className="h-3.5 w-3.5" />
              Razorpay Buildathon Track 03: AI Revenue Recovery
            </span>
            <h2 className="mt-4 text-3xl font-bold tracking-tight text-white sm:text-4xl">
              See RecoverAI recover a payment in real time.
            </h2>
            <p className="mt-3 text-sm text-slate-300 leading-relaxed">
              Explore the live operator dashboard, trigger simulated webhooks, inspect Expected Value calculations, and observe immutable audit trails.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link
                href="/login"
                className="inline-flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/30 transition-all hover:bg-brand-500 focus:outline-none"
              >
                Launch live demo
                <ArrowRight className="h-4 w-4" />
              </Link>
              <Link
                href="/login"
                className="inline-flex items-center rounded-xl border border-white/20 bg-white/5 px-5 py-3 text-sm font-semibold text-white backdrop-blur transition-all hover:bg-white/10"
              >
                Sign in to Dashboard
              </Link>
            </div>
          </div>

          {/* Background decorative elements */}
          <div className="absolute right-0 bottom-0 top-0 hidden w-1/3 opacity-20 lg:block">
            <div className="h-full w-full bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-brand-400 via-transparent to-transparent" />
          </div>
        </div>
      </div>

      {/* Footer link bar */}
      <div className="border-t border-slate-100 bg-slate-50/50 py-10">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-6 px-4 sm:flex-row sm:px-6 lg:px-8">
          <div className="flex items-center gap-2.5">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-ink-950 text-white">
              <CircleDollarSign className="h-3.5 w-3.5" />
            </div>
            <span className="text-sm font-bold tracking-tight text-ink-950">RecoverAI</span>
            <span className="text-xs text-slate-400">·</span>
            <span className="text-xs text-slate-500">AI Revenue Recovery &amp; Payment Reliability Engine</span>
          </div>

          <div className="flex flex-wrap items-center gap-6 text-xs font-medium text-slate-500">
            <a href="#how-it-works" className="hover:text-ink-950">
              How it works
            </a>
            <a href="#safety" className="hover:text-ink-950">
              Safety &amp; Guardrails
            </a>
            <a href="#experiments" className="hover:text-ink-950">
              Experiments
            </a>
            <a href="#architecture" className="hover:text-ink-950">
              Architecture
            </a>
            <Link href="/login" className="font-semibold text-brand-600 hover:text-brand-700">
              Demo Access
            </Link>
          </div>
        </div>

        <div className="mx-auto mt-6 max-w-7xl px-4 text-center text-[11px] text-slate-400 sm:px-6 lg:px-8">
          Built for Razorpay AI Buildathon 2026. All figures in public demo are simulated in Razorpay TEST MODE.
        </div>
      </div>
    </footer>
  );
}
