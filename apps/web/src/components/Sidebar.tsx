"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { NavLink } from "./ui";
import { useAuth } from "@/lib/auth";
import {
  Activity,
  BadgeIndianRupee,
  ChartNoAxesCombined,
  CircleDollarSign,
  ClipboardCheck,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  ScrollText,
  ShieldCheck,
  Unplug,
  type LucideIcon,
} from "lucide-react";

interface NavigationItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

const NAV: NavigationItem[] = [
  { href: "/overview", label: "Overview", icon: LayoutDashboard },
  { href: "/incidents", label: "Recovery Incidents", icon: BadgeIndianRupee },
  { href: "/approvals", label: "Approval Queue", icon: ClipboardCheck },
  { href: "/strategies", label: "Recovery Strategies", icon: ChartNoAxesCombined },
  { href: "/experiments", label: "Experiments", icon: FlaskConical },
  { href: "/audit", label: "Audit Log", icon: ScrollText },
  { href: "/policies", label: "Policies", icon: ShieldCheck },
  { href: "/integrations", label: "Integrations", icon: Unplug },
  { href: "/system", label: "System Health", icon: Activity },
];

function isActive(pathname: string, href: string) {
  return pathname === href || (href !== "/overview" && pathname.startsWith(href));
}

export function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
      <div className="flex min-h-16 items-center gap-2.5 border-b border-slate-100 px-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-control)] bg-ink-950 text-white shadow-sm">
          <CircleDollarSign aria-hidden="true" className="h-4 w-4" />
        </div>
        <div>
          <div className="text-sm font-semibold tracking-[-0.02em] text-ink-950">RecoverAI</div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-400">Revenue reliability</div>
        </div>
      </div>

      <nav className="flex-1 space-y-0.5 overflow-y-auto p-2.5">
        {NAV.map((item) => {
          const active = isActive(pathname, item.href);
          const Icon = item.icon;
          return (
            <NavLink key={item.href} href={item.href} active={active}>
              <Icon aria-hidden="true" className="h-4 w-4 shrink-0" strokeWidth={1.8} />
              {item.label}
            </NavLink>
          );
        })}
      </nav>

      <div className="border-t border-slate-100 p-3">
        <div className="flex items-center gap-2.5 rounded-[var(--radius-control)] bg-slate-50 p-2.5 ring-1 ring-inset ring-slate-100">
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-600 text-[11px] font-bold text-white">
            {user?.fullName?.charAt(0) || "U"}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-xs font-semibold text-slate-800">{user?.fullName || "…"}</div>
            <div className="text-[10px] text-slate-400">{user?.role || "…"}</div>
          </div>
          <button onClick={logout} aria-label="Sign out" title="Sign out" className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white hover:text-slate-700">
            <LogOut aria-hidden="true" className="h-4 w-4" strokeWidth={1.8} />
          </button>
        </div>
      </div>
    </aside>
  );
}

export function MobileNav() {
  const pathname = usePathname();
  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur lg:hidden">
      <div className="flex min-h-14 items-center gap-2.5 px-4">
        <span className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-control)] bg-ink-950 text-white"><CircleDollarSign aria-hidden="true" className="h-4 w-4" /></span>
        <div className="text-sm font-semibold tracking-tight text-ink-950">RecoverAI</div>
        <span className="ml-auto rounded-full bg-amber-50 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-amber-700 ring-1 ring-inset ring-amber-200">Test mode</span>
      </div>
      <nav aria-label="Primary navigation" className="flex gap-1 overflow-x-auto px-3 pb-2">
        {NAV.map((item) => {
          const active = isActive(pathname, item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`flex min-h-11 items-center whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                active ? "bg-brand-50 text-brand-700" : "text-slate-500 hover:bg-slate-100 hover:text-ink-950"
              }`}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
    </header>
  );
}
