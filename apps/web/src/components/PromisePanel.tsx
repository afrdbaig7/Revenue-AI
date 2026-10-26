"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { IncidentDetail } from "@/lib/types";
import { formatDateTime, formatINR } from "@/lib/format";
import { Button, Card, StatusBadge } from "@/components/ui";
import { QueryFailure } from "@/components/QueryFailure";

export function PromisePanel({ detail }: { detail: IncidentDetail }) {
  const { can } = useAuth();
  const cache = useQueryClient();
  const [text, setText] = useState("");
  const [when, setWhen] = useState("");
  const [channel, setChannel] = useState("WHATSAPP");
  const [amount, setAmount] = useState(String(detail.incident.amountMinor));
  const [saved, setSaved] = useState(false);
  const create = useMutation({
    mutationFn: () => api.post(`/incidents/${detail.incident.id}/promise`, {
      text, amountMinor: Number(amount), promisedAt: new Date(`${when}:00+05:30`).toISOString(), channel,
    }),
    onSuccess: () => { setSaved(true); setText(""); void cache.invalidateQueries({ queryKey: ["incident", detail.incident.id] }); void cache.invalidateQueries({ queryKey: ["audit", detail.incident.id] }); },
  });
  const open = !["RECOVERED", "FAILED", "BLOCKED", "CLOSED", "LATE_AUTHORIZED", "OPTED_OUT", "EXPIRED", "CANCELLED"].includes(detail.incident.status);
  return <Card title="Promise to pay" className="mt-4">
    <ul className="space-y-2">{(detail.promises ?? []).map((promise) => <li key={promise.id} className="flex flex-wrap items-center gap-3 text-xs">
      <span>{formatINR(promise.promisedAmountMinor)} by {formatDateTime(promise.promisedAt)} IST via {promise.preferredChannel}</span><StatusBadge status={promise.status} />
    </li>)}</ul>
    {can("recovery:manage") && open && detail.incident.customerId ? <form className="mt-3 grid gap-3 sm:grid-cols-2" onSubmit={(event) => { event.preventDefault(); setSaved(false); create.mutate(); }}>
      <label className="text-xs">Customer commitment <textarea required maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} className="mt-1 block w-full rounded border p-2" /></label>
      <label className="text-xs">Confirmed date and time (IST) <input required type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} className="mt-1 block w-full rounded border p-2" /></label>
      <label className="text-xs">Amount (paise) <input required type="number" min={1} max={detail.incident.amountMinor} step={1} value={amount} onChange={(e) => setAmount(e.target.value)} className="mt-1 block w-full rounded border p-2" /></label>
      <label className="text-xs">Reminder channel <select value={channel} onChange={(e) => setChannel(e.target.value)} className="mt-1 block w-full rounded border p-2"><option>WHATSAPP</option><option>EMAIL</option><option>SMS</option></select></label>
      <p className="text-xs text-slate-500 sm:col-span-2">Confirm the customer commitment before saving. The date must fall within the recovery window. Delivery remains subject to policy and configured notification support.</p>
      <Button type="submit" disabled={create.isPending}>{create.isPending ? "Saving…" : "Record promise"}</Button>
    </form> : null}
    {saved ? <p role="status" className="mt-2 text-xs text-emerald-700">Promise recorded.</p> : null}
    <QueryFailure error={create.error} />
  </Card>;
}
