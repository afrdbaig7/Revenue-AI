"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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

  const rows = integrations.data ?? [];

  return (
    <div>
      <PageHeader title="Integrations" subtitle="Connect payment providers. Only Razorpay TEST MODE is supported — no live money." />

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
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] text-amber-600">{i.modeLabel}</span>
                  <Button
                    variant="secondary"
                    onClick={() =>
                      api.post(`/integrations/${i.id}/toggle`).then(() => queryClient.invalidateQueries({ queryKey: ["integrations"] }))
                    }
                  >
                    {i.active ? "Disable" : "Enable"}
                  </Button>
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
            <label className="block">
              <span className="text-xs font-medium text-slate-600">Key ID</span>
              <input {...register("keyId")} placeholder="rzp_test_…" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-slate-600">Key secret</span>
              <input type="password" {...register("keySecret")} placeholder="••••••••" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-slate-600">Webhook secret</span>
              <input type="password" {...register("webhookSecret")} placeholder="••••••••" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
            </label>
            <div className="md:col-span-3 flex items-center gap-3">
              <Button type="submit" disabled={configure.isPending}>{configure.isPending ? "Saving…" : "Save test credentials"}</Button>
              <span className="text-[11px] text-slate-400">
                Encrypted at rest (AES-256-GCM). Never returned to the browser. When no credentials exist,
                the fixture-based mock provider is used and every result is labeled SIMULATED.
              </span>
            </div>
          </form>
        </Card>
      </div>
    </div>
  );
}
