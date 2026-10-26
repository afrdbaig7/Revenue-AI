"use client";

import { QueryFailure } from "@/components/QueryFailure";

import { useTenantQuery as useQuery } from "@/lib/queries";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import type { PolicySet } from "@/lib/types";
import { PageHeader, Card, Skeleton, EmptyState, Button } from "@/components/ui";
import { useForm } from "react-hook-form";
import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth";
import { policySchema } from "@/lib/validation";

type PolicyForm = Omit<PolicySet, "id" | "version" | "createdAt" | "updatedAt" | "active" | "name">;

export default function PoliciesPage() {
  const { can } = useAuth();
  const [baseVersion, setBaseVersion] = useState<number | null>(null);
  const queryClient = useQueryClient();
  const [saved, setSaved] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [review, setReview] = useState<PolicyForm | null>(null);

  const policies = useQuery({
    queryKey: ["policies"],
    queryFn: () => api.get<PolicySet[]>("/policies"),
  });

  const policy = policies.data?.find((p) => p.active);

  const { register, handleSubmit, reset, formState: { isDirty } } = useForm<PolicyForm>();
  useEffect(() => {
    if (policy && !isDirty) {
      reset(policySchema.parse(policy));
      setBaseVersion(policy.version);
    }
  }, [policy, isDirty, reset]);
  useEffect(() => {
    if (!isDirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty]);

  const update = useMutation({
    mutationFn: async (values: PolicyForm) => {
      const parsed = policySchema.safeParse(values);
      if (!parsed.success) throw new Error(parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("; "));
      return api.put<PolicySet>(`/policies/${policy!.id}`, { ...parsed.data, version: baseVersion });
    },
    onSuccess: (updated) => {
      reset(policySchema.parse(updated));
      setBaseVersion(updated.version);
      setSaved("Policy updated — active immediately for all new evaluations.");
      setError(null);
      setReview(null);
      queryClient.invalidateQueries({ queryKey: ["policies"] });
      setTimeout(() => setSaved(null), 4000);
    },
    onError: (e) => setError(e.message || "Update failed"),
  });

  if (policies.isError) return <QueryFailure error={policies.error} retry={() => { void policies.refetch(); }} />;

  if (policies.isLoading) return <div className="space-y-3">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24" />)}</div>;
  if (!policy) return <EmptyState title="No policy set configured" />;

  return (
    <div>
      <PageHeader
        title="Policy Configuration"
        subtitle="Deterministic, bounded controls. These limits are enforced by the platform — the AI cannot override them."
      />

      {saved ? <div className="mb-3 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700 ring-1 ring-inset ring-emerald-200">{saved}</div> : null}
      {error ? <div className="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">{error}</div> : null}

      <form onSubmit={handleSubmit((values) => {
        const parsed = policySchema.safeParse(values);
        if (!parsed.success) { setError(parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("; ")); return; }
        setError(null); setReview(parsed.data);
      })} className="grid gap-4 lg:grid-cols-2">
        <fieldset disabled={!can("policies:write") || update.isPending} className="contents">
        <Card title="Attempt & contact bounds">
          <div className="grid grid-cols-2 gap-4">
            <NumberField label="Max retries per incident" unit="" {...register("maxRetries", { valueAsNumber: true })} />
            <NumberField label="Max contact attempts" unit="" {...register("maxContactAttempts", { valueAsNumber: true })} />
            <NumberField label="Contact cooldown (hours)" unit="h" {...register("contactCooldownHours", { valueAsNumber: true })} />
            <NumberField label="Recovery window" unit="h" {...register("recoveryWindowHours", { valueAsNumber: true })} />
            <NumberField label="Min recoverable amount (paise)" unit="" {...register("minimumRecoverableAmount", { valueAsNumber: true })} />
            <NumberField label="Approval required above (paise)" unit="" {...register("requireApprovalAboveAmount", { valueAsNumber: true })} />
          </div>
        </Card>

        <Card title="Strategy gates">
          <div className="grid grid-cols-2 gap-2">
            <Toggle label="Payment links" {...register("allowPaymentLinks")} />
            <Toggle label="Delayed retry" {...register("allowDelayedRetry")} />
            <Toggle label="Email nudges" {...register("allowEmail")} />
            <Toggle label="WhatsApp nudges" {...register("allowWhatsApp")} />
            <Toggle label="SMS nudges" {...register("allowSms")} />
            <Toggle label="Discounts" {...register("allowDiscounts")} />
          </div>
          <div className="mt-4">
            <NumberField label="Max discount percent" unit="%" {...register("maxDiscountPercent", { valueAsNumber: true })} />
          </div>
        </Card>

        <div className="lg:col-span-2 flex justify-end">
          <Button type="submit" disabled={update.isPending || !isDirty}>Review changes</Button>
        </div>
        </fieldset>
      </form>
      {review ? <Card title={`Review policy changes (version ${baseVersion})`} className="mt-4">
        <table className="data-table w-full"><thead><tr><th>Setting</th><th>Current</th><th>Proposed</th></tr></thead><tbody>
          {(Object.keys(review) as (keyof PolicyForm)[]).filter((key) => review[key] !== policy[key]).map((key) => <tr key={key}><td>{key.replace(/([A-Z])/g, " $1")}</td><td>{String(policy[key])}</td><td>{String(review[key])}</td></tr>)}
        </tbody></table>
        <div className="mt-3 flex gap-2"><Button disabled={update.isPending} onClick={() => update.mutate(review)}>{update.isPending ? "Saving…" : "Confirm policy changes"}</Button><Button variant="secondary" onClick={() => setReview(null)}>Back to editing</Button></div>
      </Card> : null}
      {can("audit:read") ? <p className="mt-4 text-xs"><a className="text-brand-700 underline" href="/audit?eventType=POLICY_UPDATED">View policy change history</a></p> : null}
    </div>
  );
}

function NumberField({ label, unit, ...props }: { label: string; unit: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <div className="relative mt-1">
        <input
          type="number" min={0} step={1} required
          {...props}
          className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm tabular-nums focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
        />
        {unit ? <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">{unit}</span> : null}
      </div>
    </label>
  );
}

function Toggle({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex cursor-pointer items-center justify-between rounded-lg border border-slate-200 px-3 py-2.5">
      <span className="text-xs font-medium text-slate-700">{label}</span>
      <input type="checkbox" {...props} className="h-4 w-4 accent-brand-600" />
    </label>
  );
}
