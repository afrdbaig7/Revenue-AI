"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { ChevronRight, ShieldCheck, User } from "lucide-react";

const ROUTE_NAMES: Record<string, { group: string; name: string }> = {
  "/overview": { group: "Monitor", name: "Overview" },
  "/incidents": { group: "Monitor", name: "Recovery Incidents" },
  "/approvals": { group: "Monitor", name: "Approval Queue" },
  "/strategies": { group: "Optimize", name: "Recovery Strategies" },
  "/policies": { group: "Optimize", name: "Policies" },
  "/experiments": { group: "Optimize", name: "Experiments" },
  "/integrations": { group: "Platform", name: "Integrations" },
  "/audit": { group: "Platform", name: "Audit Log" },
  "/system": { group: "Platform", name: "System Health" },
};

export function Topbar() {
  const pathname = usePathname();
  const { user } = useAuth();

  const currentRoute = ROUTE_NAMES[pathname] || {
    group: "Dashboard",
    name: pathname.startsWith("/incidents/") ? "Incident Details" : "Workspace",
  };

  return (
    <header className="sticky top-0 z-20 flex h-14 w-full items-center justify-between border-b border-slate-200/80 bg-white/95 px-4 backdrop-blur-sm sm:px-6 lg:px-8">
      {/* Breadcrumbs */}
      <div className="flex items-center gap-2 text-xs">
        <span className="font-medium text-slate-400">{currentRoute.group}</span>
        <ChevronRight className="h-3 w-3 text-slate-300" />
        <span className="font-semibold text-ink-950">{currentRoute.name}</span>
      </div>

      {/* Right Utility Actions */}
      <div className="flex items-center gap-3">
        {/* Test Mode Badge */}
        <div className="flex items-center gap-1.5 rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-800 ring-1 ring-inset ring-amber-200">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          <span>Razorpay TEST MODE</span>
        </div>

        {/* User Pill */}
        <div className="hidden items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-700 sm:flex">
          <span className="font-medium">{user?.fullName || "Operator"}</span>
          <span className="rounded bg-slate-200/80 px-1.5 py-0.2 text-[9px] font-bold uppercase text-slate-600">
            {user?.role || "OPERATOR"}
          </span>
        </div>
      </div>
    </header>
  );
}
