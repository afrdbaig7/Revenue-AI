"use client";

import { AlertCircle, Info, LoaderCircle } from "lucide-react";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { humanize, toneFor, type Tone } from "@/lib/status";
import { classes, toneClasses, toneDots } from "./styles";

export function Badge({ children, tone = "gray", dot = false, className }: { children: ReactNode; tone?: Tone; dot?: boolean; className?: string }) {
  return (
    <span className={classes("inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-semibold leading-5 ring-1 ring-inset", toneClasses[tone], className)}>
      {dot ? <span aria-hidden="true" className={classes("h-1.5 w-1.5 rounded-full", toneDots[tone])} /> : null}
      {children}
    </span>
  );
}

export function StatusBadge({ status }: { status: string | null | undefined }) {
  return <Badge tone={toneFor(status)} dot>{humanize(status)}</Badge>;
}

export function StatCard({ label, value, sub, accent = "slate" }: { label: string; value: string; sub?: string; accent?: "slate" | "green" | "amber" | "blue" | "red" }) {
  const accents = { slate: "text-ink-950", green: "text-mint-600", amber: "text-amber-700", blue: "text-brand-600", red: "text-danger-600" };
  return (
    <div className="card p-4 sm:p-5">
      <div className="text-[11px] font-semibold uppercase tracking-[0.07em] text-slate-500">{label}</div>
      <div className={classes("mt-1.5 text-2xl font-semibold tabular-nums tracking-[-0.025em]", accents[accent])}>{value}</div>
      {sub ? <div className="mt-1 text-xs leading-5 text-slate-500">{sub}</div> : null}
    </div>
  );
}

export function Metric({ label, value, detail, tone = "navy", className }: { label: string; value: ReactNode; detail?: ReactNode; tone?: "navy" | "brand" | "green" | "amber" | "red"; className?: string }) {
  const valueTone = { navy: "text-ink-950", brand: "text-brand-600", green: "text-mint-600", amber: "text-amber-700", red: "text-danger-600" };
  return (
    <div className={className}>
      <div className="text-[11px] font-semibold uppercase tracking-[0.07em] text-slate-500">{label}</div>
      <div className={classes("mt-1 text-2xl font-semibold tabular-nums tracking-tight", valueTone[tone])}>{value}</div>
      {detail ? <div className="mt-1 text-xs text-slate-500">{detail}</div> : null}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3 sm:mb-6">
      <div className="max-w-3xl">
        <h1 className="text-xl font-semibold tracking-[-0.02em] text-ink-950">{title}</h1>
        {subtitle ? <p className="mt-1 text-[13px] leading-5 text-slate-500">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function Card({ title, children, className, contentClassName, action }: { title?: string; children: ReactNode; className?: string; contentClassName?: string; action?: ReactNode }) {
  return (
    <div className={classes("card", className)}>
      {title ? (
        <div className="flex min-h-11 items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 sm:px-5">
          <h2 className="text-[13px] font-semibold text-ink-950">{title}</h2>
          {action}
        </div>
      ) : null}
      <div className={classes("p-4 sm:p-5", contentClassName)}>{children}</div>
    </div>
  );
}

export function Skeleton({ className, label = "Loading" }: { className?: string; label?: string }) {
  return <div className={classes("skeleton", className)} role="status" aria-label={label}><span className="sr-only">{label}</span></div>;
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50/60 px-6 py-12 text-center">
      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-white text-slate-400 ring-1 ring-slate-200"><Info aria-hidden="true" className="h-4 w-4" /></div>
      <div className="mt-3 text-sm font-semibold text-ink-950">{title}</div>
      {hint ? <p className="mt-1 max-w-md text-xs leading-5 text-slate-500">{hint}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function ErrorState({ title = "Something went wrong", description, onRetry, details }: { title?: string; description: string; onRetry?: () => void; details?: string }) {
  return (
    <div role="alert" className="rounded-xl border border-danger-100 bg-danger-50 p-4">
      <div className="flex items-start gap-3">
        <AlertCircle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold text-danger-700">{title}</div>
          <p className="mt-1 text-xs leading-5 text-slate-600">{description}</p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            {onRetry ? <Button variant="secondary" size="sm" onClick={onRetry}>Try again</Button> : null}
            {details ? <details className="text-xs text-slate-500"><summary className="cursor-pointer font-medium">Developer details</summary><pre className="mt-2 max-h-40 max-w-full overflow-auto whitespace-pre-wrap rounded-lg bg-white p-3 font-mono text-[11px] ring-1 ring-slate-200">{details}</pre></details> : null}
          </div>
        </div>
      </div>
    </div>
  );
}

export function Button({ children, variant = "primary", size = "md", loading = false, disabled, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" | "ghost"; size?: "sm" | "md"; loading?: boolean }) {
  const variants = {
    primary: "bg-brand-600 text-white shadow-sm hover:bg-brand-700",
    secondary: "bg-white text-ink-800 ring-1 ring-inset ring-slate-200 hover:bg-slate-50 hover:ring-slate-300",
    danger: "bg-danger-500 text-white shadow-sm hover:bg-danger-600",
    ghost: "text-slate-600 hover:bg-slate-100 hover:text-ink-950",
  };
  const sizes = { sm: "min-h-8 px-2.5 py-1 text-xs", md: "min-h-9 px-3 py-1.5 text-[13px]" };
  return (
    <button {...props} disabled={disabled || loading} aria-busy={loading || undefined} className={classes("inline-flex items-center justify-center gap-1.5 rounded-[var(--radius-control)] font-semibold transition-[background-color,color,box-shadow] duration-200 disabled:cursor-not-allowed disabled:opacity-50", variants[variant], sizes[size], className)}>
      {loading ? <LoaderCircle aria-hidden="true" className="h-3.5 w-3.5 animate-spin" /> : null}
      {children}
    </button>
  );
}
