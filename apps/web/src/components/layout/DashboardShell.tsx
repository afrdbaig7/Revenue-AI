"use client";

import type { ReactNode } from "react";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";
import { MobileNav } from "./MobileNav";
import { PageContainer } from "./PageContainer";

export function DashboardShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[var(--color-background-soft)] text-slate-800">
      {/* Desktop Persistent Sidebar */}
      <Sidebar />

      {/* Main Content Area */}
      <div className="flex min-w-0 flex-1 flex-col">
        <MobileNav />
        <Topbar />
        <main className="flex-1">
          <PageContainer>{children}</PageContainer>
        </main>
      </div>
    </div>
  );
}
