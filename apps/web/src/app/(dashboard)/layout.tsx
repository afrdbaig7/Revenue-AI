"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { FullPageLoader } from "@/components/ui";
import { DashboardShell } from "@/components/layout";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.replace("/login");
    }
  }, [loading, user, router]);

  if (loading || !user) {
    return <FullPageLoader label="Preparing your revenue workspace" />;
  }

  return <DashboardShell>{children}</DashboardShell>;
}
