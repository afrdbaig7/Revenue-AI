"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import {
  LayoutDashboard,
  BadgeIndianRupee,
  ClipboardCheck,
  ChartNoAxesCombined,
  ShieldCheck,
  FlaskConical,
  Unplug,
  ScrollText,
  Activity,
  CircleDollarSign,
  LogOut,
  Sparkles,
  type LucideIcon,
} from "lucide-react";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  badge?: string;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    title: "MONITOR",
    items: [
      { href: "/overview", label: "Overview", icon: LayoutDashboard },
      { href: "/incidents", label: "Recovery Incidents", icon: BadgeIndianRupee },
      { href: "/approvals", label: "Approval Queue", icon: ClipboardCheck },
    ],
  },
  {
    title: "OPTIMIZE",
    items: [
      { href: "/strategies", label: "Recovery Strategies", icon: ChartNoAxesCombined },
      { href: "/policies", label: "Policies", icon: ShieldCheck },
      { href: "/experiments", label: "Experiments", icon: FlaskConical },
    ],
  },
  {
    title: "PLATFORM",
    items: [
      { href: "/integrations", label: "Integrations", icon: Unplug },
      { href: "/audit", label: "Audit Log", icon: ScrollText },
      { href: "/system", label: "System Health", icon: Activity },
    ],
  },
];

function isItemActive(pathname: string, href: string) {
  return pathname === href || (href !== "/overview" && pathname.startsWith(href));
}

export function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
      {/* Brand Header */}
      <div className="flex min-h-16 items-center gap-2.5 border-b border-slate-100 px-5">
        <div className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-control)] bg-ink-950 text-white shadow-sm ring-1 ring-ink-950/10">
          <CircleDollarSign aria-hidden="true" className="h-4 w-4" />
        </div>
        <div className="flex flex-col">
          <span className="text-sm font-bold tracking-tight text-ink-950">RecoverAI</span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-400">
            Revenue Reliability
          </span>
        </div>
      </div>

      {/* Navigation Groups */}
      <div className="flex-1 space-y-6 overflow-y-auto px-3.5 py-5">
        {NAV_GROUPS.map((group) => (
          <div key={group.title} className="space-y-1">
            <div className="px-3 pb-1 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
              {group.title}
            </div>
            <nav className="space-y-0.5" aria-label={group.title}>
              {group.items.map((item) => {
                const active = isItemActive(pathname, item.href);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={`group flex min-h-9 items-center justify-between rounded-[var(--radius-control)] px-3 py-1.5 text-xs font-semibold transition-colors ${
                      active
                        ? "bg-brand-50 text-brand-700 font-bold"
                        : "text-slate-600 hover:bg-slate-50 hover:text-ink-950"
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      <Icon
                        aria-hidden="true"
                        className={`h-4 w-4 shrink-0 transition-colors ${
                          active ? "text-brand-600" : "text-slate-400 group-hover:text-slate-600"
                        }`}
                        strokeWidth={1.8}
                      />
                      <span>{item.label}</span>
                    </div>
                  </Link>
                );
              })}
            </nav>
          </div>
        ))}
      </div>

      {/* Bottom Footer Area */}
      <div className="border-t border-slate-100 p-3.5 space-y-2.5 bg-slate-50/50">
        {/* Demo Mode Badge */}
        <div className="flex items-center justify-between rounded-lg bg-amber-50/80 px-2.5 py-1.5 text-[11px] font-medium text-amber-800 border border-amber-200/60">
          <div className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
            <span className="font-semibold">Razorpay Test Mode</span>
          </div>
          <span className="font-mono text-[9px] uppercase tracking-wider text-amber-700 font-bold">
            SANDBOX
          </span>
        </div>

        {/* User Card */}
        <div className="flex items-center gap-2.5 rounded-xl bg-white p-2.5 shadow-sm border border-slate-200/70">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-600 text-[11px] font-bold text-white shadow-xs">
            {user?.fullName?.charAt(0) || "U"}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-xs font-semibold text-ink-950">
              {user?.fullName || "Operator User"}
            </div>
            <div className="truncate text-[10px] text-slate-400">
              {user?.role || "OPERATOR"} · Demo Store
            </div>
          </div>
          <button
            onClick={logout}
            aria-label="Sign out"
            title="Sign out"
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
          >
            <LogOut aria-hidden="true" className="h-4 w-4" strokeWidth={1.8} />
          </button>
        </div>
      </div>
    </aside>
  );
}
