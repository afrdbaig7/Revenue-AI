"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { api, setAccessToken, setUnauthorizedHandler } from "./api";
import { useQueryClient } from "@tanstack/react-query";

export interface UserView {
  userId: string;
  orgId: string;
  email: string;
  fullName: string;
  role: string;
}

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserView;
}

interface AuthState {
  user: UserView | null;
  loading: boolean;
  permissions: string[];
  orgName: string;
  can: (permission: string) => boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<UserView | null>(null);
  const [loading, setLoading] = useState(true);
  const [permissions, setPermissions] = useState<string[]>([]);
  const [orgName, setOrgName] = useState("");
  const router = useRouter();

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<LoginResponse>("/auth/login", { email, password });
    await queryClient.cancelQueries();
    queryClient.clear();
    setAccessToken(null);
    const me = await api.get<{ user: UserView; permissions: string[]; orgName: string }>("/auth/me");
    setUser(me.user);
    setPermissions(me.permissions);
    setOrgName(me.orgName);
    router.replace("/overview");
  }, [router, queryClient]);

  const logout = useCallback(async () => {
    try {
      await api.post("/auth/logout");
    } catch {
      /* ignore */
    }
    setAccessToken(null);
    setUser(null);
    setPermissions([]);
    setOrgName("");
    await queryClient.cancelQueries();
    queryClient.clear();
    router.replace("/login");
  }, [router, queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setAccessToken(null);
      setUser(null);
      setPermissions([]);
      setOrgName("");
      void queryClient.cancelQueries().then(() => queryClient.clear());
      if (!["/", "/login"].includes(window.location.pathname)) router.replace("/login");
    });
    // Session bootstrap: try the cookie-authenticated /auth/me first (local dev).
    api
      .get<{ user: UserView; permissions: string[]; orgName: string }>("/auth/me")
      .then((me) => { setUser(me.user); setPermissions(me.permissions); setOrgName(me.orgName); })
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
    return () => setUnauthorizedHandler(null);
  }, [router, queryClient]);

  const value = useMemo(() => ({ user, loading, login, logout, permissions, orgName,
    can: (permission: string) => permissions.includes("*") || permissions.includes(permission) }), [user, loading, login, logout, permissions, orgName]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
