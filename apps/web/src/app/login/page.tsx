"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";

const schema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type Form = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { register, handleSubmit, formState } = useForm<Form>({
    defaultValues: { email: "demo@recoverai.dev", password: "DemoPass!123" },
  });

  const onSubmit = async (values: Form) => {
    setBusy(true);
    setError(null);
    try {
      await login(values.email, values.password);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Login failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      {/* Brand panel */}
      <div className="hidden w-1/2 flex-col justify-between bg-slate-950 p-10 lg:flex">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10">
            <svg className="h-5 w-5 text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <span className="text-lg font-semibold text-white">RecoverAI</span>
        </div>
        <div className="max-w-md">
          <h1 className="text-3xl font-semibold leading-tight tracking-tight text-white">
            Revenue was at risk.
            <br />
            <span className="text-emerald-400">RecoverAI closed the loop.</span>
          </h1>
          <div className="mt-6 space-y-3 text-sm text-slate-400">
            <div className="flex gap-2.5"><span className="text-emerald-400">→</span> Detected failed payments and abandoned checkouts</div>
            <div className="flex gap-2.5"><span className="text-emerald-400">→</span> Diagnosed why with confidence and evidence</div>
            <div className="flex gap-2.5"><span className="text-emerald-400">→</span> Executed policy-bounded recovery — AI recommends, software authorizes</div>
            <div className="flex gap-2.5"><span className="text-emerald-400">→</span> Measured exactly how much revenue was recovered</div>
          </div>
        </div>
        <div className="text-xs text-slate-500">
          Razorpay TEST MODE · synthetic data · every decision auditable
        </div>
      </div>

      {/* Login form */}
      <div className="flex flex-1 items-center justify-center bg-slate-50 p-6">
        <div className="w-full max-w-sm">
          <div className="mb-6 lg:hidden">
            <div className="text-xl font-semibold text-slate-900">RecoverAI</div>
            <div className="text-xs text-slate-500">AI Revenue Recovery &amp; Payment Reliability Engine</div>
          </div>
          <h2 className="text-lg font-semibold text-slate-900">Sign in to the dashboard</h2>
          <p className="mt-1 text-sm text-slate-500">Demo credentials are pre-filled.</p>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4">
            <div>
              <label htmlFor="email" className="text-xs font-medium text-slate-600">Email</label>
              <input
                {...register("email")}
                id="email"
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
              />
              {formState.errors.email && <p className="mt-1 text-xs text-rose-600">{formState.errors.email.message}</p>}
            </div>
            <div>
              <label htmlFor="password" className="text-xs font-medium text-slate-600">Password</label>
              <input
                type="password"
                id="password"
                {...register("password")}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
              />
              {formState.errors.password && <p className="mt-1 text-xs text-rose-600">{formState.errors.password.message}</p>}
            </div>
            {error && (
              <div className="rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700 ring-1 ring-inset ring-rose-200">{error}</div>
            )}
            <button
              type="submit"
              disabled={busy}
              className="w-full rounded-lg bg-brand-600 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700 disabled:bg-slate-300"
            >
              {busy ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <div className="mt-6 rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-500">
            <div className="font-semibold text-slate-700">Demo accounts</div>
            <div className="mt-1.5 space-y-0.5">
              <div>demo@recoverai.dev / DemoPass!123 — Owner</div>
              <div>operator@recoverai.dev / DemoPass!123 — Operator</div>
              <div>analyst@recoverai.dev / DemoPass!123 — Analyst</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
