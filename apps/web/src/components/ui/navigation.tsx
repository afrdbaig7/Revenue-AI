"use client";

import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useId, type ReactNode } from "react";
import { Button } from "./core";
import { classes } from "./styles";

export function Progress({ value, label, tone = "brand" }: { value: number; label?: string; tone?: "brand" | "green" | "amber" }) {
  const normalized = Math.min(100, Math.max(0, value));
  const fills = { brand: "bg-brand-500", green: "bg-mint-500", amber: "bg-amber-500" };
  return (
    <div>
      {label ? <div className="mb-1.5 flex items-center justify-between text-xs text-slate-500"><span>{label}</span><span className="tabular-nums">{Math.round(normalized)}%</span></div> : null}
      <div className="h-1.5 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(normalized)}><div className={classes("h-full rounded-full transition-[width] duration-300", fills[tone])} style={{ width: `${normalized}%` }} /></div>
    </div>
  );
}

export interface TabItem { value: string; label: string; count?: number; disabled?: boolean; }

export function Tabs({ items, value, onValueChange, ariaLabel = "Sections" }: { items: TabItem[]; value: string; onValueChange: (value: string) => void; ariaLabel?: string }) {
  const tabsId = useId();
  const move = (current: number, direction: 1 | -1) => {
    for (let offset = 1; offset <= items.length; offset += 1) {
      const candidate = items[(current + direction * offset + items.length) % items.length];
      if (!candidate.disabled) {
        onValueChange(candidate.value);
        requestAnimationFrame(() => document.getElementById(`${tabsId}-tab-${candidate.value}`)?.focus());
        return;
      }
    }
  };
  return (
    <div role="tablist" aria-label={ariaLabel} className="inline-flex max-w-full gap-1 overflow-x-auto rounded-xl bg-slate-100 p-1">
      {items.map((item, index) => {
        const active = item.value === value;
        return <button key={item.value} id={`${tabsId}-tab-${item.value}`} type="button" role="tab" aria-selected={active} tabIndex={active ? 0 : -1} disabled={item.disabled} onClick={() => onValueChange(item.value)} onKeyDown={(event) => { if (event.key === "ArrowRight") { event.preventDefault(); move(index, 1); } if (event.key === "ArrowLeft") { event.preventDefault(); move(index, -1); } }} className={classes("whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors disabled:opacity-40", active ? "bg-white text-ink-950 shadow-sm" : "text-slate-500 hover:text-ink-950")}>{item.label}{item.count != null ? <span className="ml-1.5 tabular-nums text-slate-400">{item.count}</span> : null}</button>;
      })}
    </div>
  );
}

export function NavLink({ href, active, children }: { href: string; active: boolean; children: ReactNode }) {
  return <Link href={href} aria-current={active ? "page" : undefined} className={classes("flex min-h-10 items-center gap-2.5 rounded-[var(--radius-control)] px-3 py-2 text-[13px] font-semibold transition-colors", active ? "bg-brand-50 text-brand-700" : "text-slate-500 hover:bg-slate-50 hover:text-ink-950")}>{children}</Link>;
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-between text-xs text-slate-500">
      <span>Page <span className="tabular-nums">{page + 1}</span> of <span className="tabular-nums">{totalPages}</span></span>
      <div className="flex gap-1.5"><Button variant="secondary" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}><ChevronLeft aria-hidden="true" className="h-3.5 w-3.5" />Prev</Button><Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>Next<ChevronRight aria-hidden="true" className="h-3.5 w-3.5" /></Button></div>
    </nav>
  );
}
