"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { FullPageLoader } from "@/components/ui";

export default function Home() {
  const router = useRouter();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (!loading) {
      router.replace(user ? "/overview" : "/login");
    }
  }, [loading, user, router]);

  return <FullPageLoader label="Checking your session" />;
}
