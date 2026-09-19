"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CircleDollarSign, Menu, X, ArrowRight } from "lucide-react";
import { useAuth } from "@/lib/auth";

export function LandingNav() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const { user } = useAuth();

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  return (
    <header
      className={`sticky top-0 z-50 transition-all duration-200 ${
        scrolled
          ? "border-b border-slate-200/80 bg-white/95 backdrop-blur-md shadow-sm"
          : "border-b border-transparent bg-transparent"
      }`}
    >
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Brand */}
        <Link href="/" className="flex items-center gap-2.5 focus:outline-none">
          <div className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-control)] bg-ink-950 text-white shadow-sm ring-1 ring-ink-950/10">
            <CircleDollarSign aria-hidden="true" className="h-4 w-4" />
          </div>
          <div className="flex flex-col">
            <span className="text-base font-bold tracking-tight text-ink-950">RecoverAI</span>
            <span className="hidden text-[9px] font-semibold uppercase tracking-[0.08em] text-slate-400 sm:inline">
              Revenue Reliability Engine
            </span>
          </div>
        </Link>

        {/* Desktop Navigation Links */}
        <nav className="hidden items-center gap-7 md:flex" aria-label="Main Navigation">
          <a
            href="#how-it-works"
            className="text-[13px] font-medium text-slate-600 transition-colors hover:text-ink-950"
          >
            How it works
          </a>
          <a
            href="#safety"
            className="text-[13px] font-medium text-slate-600 transition-colors hover:text-ink-950"
          >
            Safety & Boundaries
          </a>
          <a
            href="#experiments"
            className="text-[13px] font-medium text-slate-600 transition-colors hover:text-ink-950"
          >
            A/B Experiments
          </a>
          <a
            href="#architecture"
            className="text-[13px] font-medium text-slate-600 transition-colors hover:text-ink-950"
          >
            Architecture
          </a>
        </nav>

        {/* Right CTA Actions */}
        <div className="hidden items-center gap-3 md:flex">
          {user ? (
            <Link
              href="/overview"
              className="inline-flex items-center gap-1.5 rounded-[var(--radius-control)] bg-brand-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition-all hover:bg-brand-700"
            >
              Open Dashboard
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          ) : (
            <>
              <Link
                href="/login"
                className="text-xs font-semibold text-slate-600 transition-colors hover:text-ink-950"
              >
                Sign in
              </Link>
              <Link
                href="/login"
                className="inline-flex items-center gap-1.5 rounded-[var(--radius-control)] bg-brand-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition-all hover:bg-brand-700"
              >
                Launch demo
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </>
          )}
        </div>

        {/* Mobile menu button */}
        <div className="flex md:hidden">
          <button
            type="button"
            onClick={() => setMobileOpen(!mobileOpen)}
            aria-label={mobileOpen ? "Close menu" : "Open menu"}
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 focus:outline-none"
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      {/* Mobile drawer */}
      {mobileOpen ? (
        <div className="border-b border-slate-200 bg-white px-4 pt-2 pb-6 md:hidden">
          <nav className="flex flex-col gap-3">
            <a
              href="#how-it-works"
              onClick={() => setMobileOpen(false)}
              className="px-2 py-1.5 text-sm font-medium text-slate-700 hover:text-ink-950"
            >
              How it works
            </a>
            <a
              href="#safety"
              onClick={() => setMobileOpen(false)}
              className="px-2 py-1.5 text-sm font-medium text-slate-700 hover:text-ink-950"
            >
              Safety & Boundaries
            </a>
            <a
              href="#experiments"
              onClick={() => setMobileOpen(false)}
              className="px-2 py-1.5 text-sm font-medium text-slate-700 hover:text-ink-950"
            >
              A/B Experiments
            </a>
            <a
              href="#architecture"
              onClick={() => setMobileOpen(false)}
              className="px-2 py-1.5 text-sm font-medium text-slate-700 hover:text-ink-950"
            >
              Architecture
            </a>
            <div className="mt-2 flex flex-col gap-2 border-t border-slate-100 pt-3">
              <Link
                href="/login"
                onClick={() => setMobileOpen(false)}
                className="w-full rounded-[var(--radius-control)] border border-slate-200 bg-white py-2 text-center text-xs font-semibold text-slate-800"
              >
                Sign in
              </Link>
              <Link
                href="/overview"
                onClick={() => setMobileOpen(false)}
                className="w-full rounded-[var(--radius-control)] bg-brand-600 py-2 text-center text-xs font-semibold text-white shadow-sm"
              >
                Launch live demo →
              </Link>
            </div>
          </nav>
        </div>
      ) : null}
    </header>
  );
}
