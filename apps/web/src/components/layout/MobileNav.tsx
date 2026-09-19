"use client";

import { useState, useEffect } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import {
  Menu,
  X,
  CircleDollarSign,
  LayoutDashboard,
  BadgeIndianRupee,
  ClipboardCheck,
  ChartNoAxesCombined,
  ShieldCheck,
  FlaskConical,
  Unplug,
  ScrollText,
  Activity,
  LogOut,
  type LucideIcon,
} from "lucide-react";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

const NAV_GROUPS: Array<{ title: string; items: NavItem[] }> = [
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

export function MobileNav() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const { user, logout } = useAuth();

  // Close drawer on route change
  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  // Handle ESC key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    if (open) {
      document.addEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "";
    };
  }, [open]);

  return (
    <div className="lg:hidden">
      {/* Mobile Top Header */}
      <div className="flex h-14 items-center justify-between border-b border-slate-200 bg-white px-4">
        <div className="flex items-center gap-2.5">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-ink-950 text-white">
            <CircleDollarSign className="h-3.5 w-3.5" />
          </div>
          <span className="text-sm font-bold tracking-tight text-ink-950">RecoverAI</span>
        </div>

        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="Open navigation menu"
          className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 focus:outline-none"
        >
          <Menu className="h-5 w-5" />
        </button>
      </div>

      {/* Backdrop */}
      {open ? (
        <div
          className="fixed inset-0 z-40 bg-ink-950/40 backdrop-blur-xs transition-opacity"
          onClick={() => setOpen(false)}
          aria-hidden="true"
        />
      ) : null}

      {/* Drawer Panel */}
      <div
        className={`fixed inset-y-0 left-0 z-50 flex w-72 flex-col bg-white shadow-2xl transition-transform duration-250 ease-in-out ${
          open ? "translate-x-0" : "-translate-x-full"
        }`}
        role="dialog"
        aria-modal="true"
        aria-label="Mobile Navigation"
      >
        {/* Drawer Header */}
        <div className="flex h-14 items-center justify-between border-b border-slate-100 px-4">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-ink-950 text-white">
              <CircleDollarSign className="h-3.5 w-3.5" />
            </div>
            <span className="text-sm font-bold text-ink-950">RecoverAI</span>
          </div>
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label="Close navigation menu"
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Drawer Nav Items */}
        <div className="flex-1 space-y-5 overflow-y-auto px-3.5 py-4">
          {NAV_GROUPS.map((group) => (
            <div key={group.title} className="space-y-1">
              <div className="px-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => {
                  const active = pathname === item.href || (item.href !== "/overview" && pathname.startsWith(item.href));
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={`flex min-h-9 items-center gap-2.5 rounded-lg px-3 py-1.5 text-xs font-semibold ${
                        active
                          ? "bg-brand-50 text-brand-700 font-bold"
                          : "text-slate-600 hover:bg-slate-50 hover:text-ink-950"
                      }`}
                    >
                      <Icon className={`h-4 w-4 ${active ? "text-brand-600" : "text-slate-400"}`} />
                      <span>{item.label}</span>
                    </Link>
                  );
                })}
              </div>
            </div>
          ))}
        </div>

        {/* Drawer Footer */}
        <div className="border-t border-slate-100 p-4 bg-slate-50">
          <div className="flex items-center justify-between">
            <div className="min-w-0 flex-1">
              <div className="truncate text-xs font-semibold text-slate-800">
                {user?.fullName || "Operator User"}
              </div>
              <div className="text-[10px] text-slate-400">{user?.role || "OPERATOR"}</div>
            </div>
            <button
              onClick={() => {
                setOpen(false);
                logout();
              }}
              className="rounded-lg p-1.5 text-slate-500 hover:bg-white hover:text-danger-600"
              title="Sign out"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
