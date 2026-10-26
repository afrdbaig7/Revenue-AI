"use client";

import { QueryFailure } from "@/components/QueryFailure";

import { useTenantQuery as useQuery } from "@/lib/queries";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import type { IntegrationView } from "@/lib/types";
import { PageHeader, Card, Skeleton, EmptyState, Button, ModePill } from "@/components/ui";
import { useForm } from "react-hook-form";
import { useState } from "react";

interface Form {
  keyId: string;
  keySecret: string;
  webhookSecret: string;
}

export default function IntegrationsPage() {
  const { can } = useAuth();
  const [verified, setVerified] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, reset } = useForm<Form>();

  const integrations = useQuery({
    queryKey: ["integrations"],
    queryFn: () => api.get<IntegrationView[]>("/integrations"),
  });

  const configure = useMutation({
    mutationFn: (v: Form) => api.post<IntegrationView>("/integrations/razorpay/test", v),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["integrations"] });
      reset();
      setError(null);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Configuration failed"),
  });

  const toggle = useMutation({
    mutationFn: (id: string) => api.post(`/integrations/${id}/toggle`),
    onSuccess: () => { setError(null); void queryClient.invalidateQueries({ queryKey: ["integrations"] }); },
    onError: (e) => setError(e.message),
  });
  const verify = useMutation({
    mutationFn: (id: string) => api.post(`/integrations/${id}/verify`),
    onSuccess: () => { setVerified("Test account connection verified."); setError(null); },
    onError: (e) => setError(e.message),
  });

  if (integrations.isError) return <QueryFailure error={integrations.error} retry={() => { void integrations.refetch(); }} />;

  const rows = integrations.data ?? [];

  return (
    <div>
      <PageHeader title="Integrations" subtitle="Connect payment providers. Only Razorpay TEST MODE is supported — no live money." />

      {error ? <div role="alert" className="mb-3 text-sm text-rose-700">{error}</div> : null}
      {verified ? <div role="status" className="mb-3 text-sm text-emerald-700">{verified}</div> : null}
      <p className="mb-4 text-sm text-slate-600">Webhook URL: <code>/api/v1/webhooks/razorpay</code>. Configure this path on your public API host in the Razorpay test dashboard, then send a test event.</p>
      {integrations.isLoading ? (
        <Skeleton className="h-28" />
      ) : rows.length === 0 ? (
        <EmptyState title="No integrations configured" hint="Configure Razorpay TEST MODE below." />
      ) : (
        <div className="space-y-3">
          {rows.map((i) => (
            <Card key={i.id}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-900 text-[10px] font-bold text-white">
                    RZP
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-slate-900">Razorpay</span>
                      <ModePill mode={i.mode} />
                    </div>
                    <div className="mt-0.5 text-xs text-slate-500">
                      {i.active ? "Active" : "Disabled"} · {i.keyIdMasked ?? "no key configured"} · webhook secret{" "}
                      {i.webhookSecretConfigured ? "configured" : "missing"}
                    </div>
                    <div className="mt-2 break-all text-xs text-slate-600">Webhook path: <code>{`/api/v1/webhooks/razorpay/${i.id}`}</code></div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] text-amber-600">{i.modeLabel}</span>
                  {can("integrations:write") ? <>
                    <Button variant="secondary" disabled={toggle.isPending} onClick={() => toggle.mutate(i.id)}>{i.active ? "Disable" : "Enable"}</Button>
                    <Button variant="secondary" disabled={verify.isPending || !i.active} onClick={() => verify.mutate(i.id)}>{verify.isPending ? "Verifying?" : "Verify connection"}</Button>
                  </> : null}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <div className="mt-6">
        <Card title="Configure Razorpay (TEST MODE)">
          {error ? <div className="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">{error}</div> : null}
          <form onSubmit={handleSubmit((v) => configure.mutate(v))} className="grid gap-4 md:grid-cols-3">
            <fieldset disabled={!can("integrations:write") || configure.isPending} className="contents">
            <label className="block">
              <span className="text-xs font-medium text-slate-600">Key ID</span>
              <input {...register("keyId", { required: true, pattern: /^rzp_test_/ })} required pattern="rzp_test_.*" placeholder="rzp_test_…" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-slate-600">Key secret</span>
              <input required autoComplete="new-password" type="password" {...register("keySecret", { required: true })} placeholder="••••••••" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-slate-600">Webhook secret</span>
              <input required autoComplete="new-password" type="password" {...register("webhookSecret", { required: true })} placeholder="••••••••" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
            </label>
            <div className="md:col-span-3 flex items-center gap-3">
              <Button type="submit" disabled={configure.isPending}>{configure.isPending ? "Saving…" : "Save test credentials"}</Button>
              <span className="text-[11px] text-slate-400">
                Encrypted at rest (AES-256-GCM). Never returned to the browser. Mock mode uses simulated adapters. Real test mode requires a configured, active test integration.
              </span>
            </div>
            </fieldset>
          </form>
        </Card>
      </div>
    </div>
  );
}
