// k6 load test — Razorpay webhook burst ingestion.
// Run:  k6 run scripts/load/webhook-burst.js
import http from "k6/http";
import { check } from "k6";
import crypto from "k6/crypto";
import encoding from "k6/encoding";

const API = __ENV.API || "http://localhost:8080";
const SECRET = __ENV.WEBHOOK_SECRET || "recoverai_demo_webhook_secret";

function signedBody(ts) {
  const body = JSON.stringify({
    id: `evt_burst_${ts}`,
    event: "payment.failed",
    created_at: Math.floor(Date.now() / 1000),
    payload: {
      payment: {
        id: `pay_burst_${ts}`,
        order_id: `order_burst_${ts}`,
        amount: 349900,
        currency: "INR",
        status: "failed",
        method: "card",
        error_code: "INSUFFICIENT_FUNDS",
        error_description: "The bank reported insufficient funds",
        failure_reason: "bank_declined",
      },
    },
  });
  const hmac = crypto.hmac("sha256", SECRET, body, "hex");
  return { body, sig: hmac };
}

export const options = {
  scenarios: {
    webhook_burst: {
      executor: "constant-vus",
      vus: 20,
      duration: "30s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<300"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const ts = `${__VU}_${Date.now()}_${__ITER}`;
  const { body, sig } = signedBody(ts);
  const res = http.post(
    `${API}/api/v1/webhooks/razorpay`,
    body,
    { headers: { "Content-Type": "application/json", "X-Razorpay-Signature": sig } },
  );
  check(res, { "200 + received": (r) => r.status === 200 && r.json("received") === true });
}
