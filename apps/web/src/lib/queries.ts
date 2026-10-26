"use client";

import { useQuery, type UseQueryOptions } from "@tanstack/react-query";
import { useAuth } from "./auth";

/** Preserve resource prefixes for invalidation while isolating all tenant data. */
export function useTenantQuery<T>(options: UseQueryOptions<T, Error, T, readonly unknown[]>) {
  const { user } = useAuth();
  return useQuery({ ...options, queryKey: [...options.queryKey, { orgId: user?.orgId }], enabled: Boolean(user) && options.enabled !== false });
}
