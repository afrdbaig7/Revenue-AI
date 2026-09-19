import type { ReactNode } from "react";

export function CustomChartTooltip({
  active,
  payload,
  label,
  valueFormatter,
}: {
  active?: boolean;
  payload?: Array<{ name: string; value: number; color: string }>;
  label?: string;
  valueFormatter?: (v: number) => string;
}) {
  if (!active || !payload || !payload.length) return null;

  return (
    <div className="rounded-xl border border-slate-200/80 bg-white/95 p-3 shadow-lg backdrop-blur-xs text-xs">
      {label ? <div className="font-semibold text-ink-950 pb-1.5 border-b border-slate-100">{label}</div> : null}
      <div className="mt-1.5 space-y-1">
        {payload.map((entry, idx) => (
          <div key={idx} className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-1.5">
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: entry.color }}
              />
              <span className="text-slate-600">{entry.name}</span>
            </div>
            <span className="font-bold tabular-nums text-ink-950">
              {valueFormatter ? valueFormatter(entry.value) : entry.value.toLocaleString("en-IN")}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
