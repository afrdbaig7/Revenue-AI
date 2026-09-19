import Link from "next/link";
import { ArrowRight, Terminal, ShieldCheck, Sparkles } from "lucide-react";
import { BackgroundMesh } from "./BackgroundMesh";
import { HeroVisual } from "./HeroVisual";

export function HeroSection() {
  return (
    <section className="relative overflow-hidden pt-8 pb-20 sm:pt-14 sm:pb-28 lg:pt-20 lg:pb-32">
      <BackgroundMesh />

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-12 lg:grid-cols-12 lg:items-center lg:gap-8">
          {/* Left Column: Value Proposition & Copy */}
          <div className="lg:col-span-6">
            {/* Eyebrow badge */}
            <div className="inline-flex items-center gap-2 rounded-full border border-brand-500/20 bg-brand-50/70 px-3.5 py-1 text-xs font-semibold text-brand-700 shadow-sm backdrop-blur-sm">
              <Sparkles className="h-3.5 w-3.5 text-brand-600" />
              <span>AI Revenue Recovery Infrastructure</span>
            </div>

            {/* Main Headline */}
            <h1 className="mt-6 text-4xl font-extrabold tracking-[-0.035em] text-ink-950 sm:text-5xl lg:text-[56px] lg:leading-[1.08]">
              Recover revenue before it disappears.
            </h1>

            {/* Supporting Copy */}
            <p className="mt-6 text-base leading-relaxed text-slate-600 sm:text-lg">
              RecoverAI detects failed revenue journeys, diagnoses why they failed, chooses the highest-value recovery strategy, and executes only actions permitted by deterministic policy.
            </p>

            {/* CTA Action Buttons */}
            <div className="mt-8 flex flex-wrap items-center gap-3.5">
              <Link
                href="/login"
                className="inline-flex items-center gap-2 rounded-[var(--radius-control)] bg-brand-600 px-5 py-3 text-sm font-semibold text-white shadow-md shadow-brand-500/25 transition-all hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
              >
                Launch live demo
                <ArrowRight className="h-4 w-4" />
              </Link>
              <a
                href="#architecture"
                className="inline-flex items-center gap-2 rounded-[var(--radius-control)] border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 shadow-sm transition-all hover:bg-slate-50 hover:text-ink-950 focus:outline-none"
              >
                <Terminal className="h-4 w-4 text-slate-500" />
                Explore architecture
              </a>
            </div>

            {/* Trust Badges */}
            <div className="mt-8 flex flex-wrap items-center gap-2 text-xs font-medium text-slate-500">
              <span className="flex items-center gap-1.5 rounded-md bg-slate-100/80 px-2.5 py-1 font-mono text-[11px] text-slate-700">
                <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" />
                Razorpay TEST MODE
              </span>
              <span className="text-slate-300">·</span>
              <span>Bounded AI</span>
              <span className="text-slate-300">·</span>
              <span>Every action auditable</span>
            </div>
          </div>

          {/* Right Column: Interactive / Realistic Dashboard Visual */}
          <div className="lg:col-span-6 lg:pl-4">
            <HeroVisual />
          </div>
        </div>
      </div>
    </section>
  );
}
