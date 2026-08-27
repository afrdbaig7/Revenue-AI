import type { Tone } from "@/lib/status";

export function classes(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(" ");
}

export const toneClasses: Record<Tone, string> = {
  gray: "bg-slate-100 text-slate-600 ring-slate-200",
  navy: "bg-sky-50 text-ink-800 ring-sky-200",
  green: "bg-mint-50 text-mint-600 ring-emerald-200",
  amber: "bg-amber-50 text-amber-700 ring-amber-200",
  red: "bg-danger-50 text-danger-700 ring-danger-100",
  violet: "bg-brand-50 text-brand-700 ring-brand-100",
};

export const toneDots: Record<Tone, string> = {
  gray: "bg-slate-400",
  navy: "bg-ink-700",
  green: "bg-mint-500",
  amber: "bg-amber-500",
  red: "bg-danger-500",
  violet: "bg-brand-500",
};
