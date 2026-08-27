#!/usr/bin/env bash
# ============================================================================
# RecoverAI demo event firer — sends Razorpay-style signed webhooks to the API.
#
#   ./scripts/demo/fire-events.sh failed     # payment.failed (creates incident)
#   ./scripts/demo/fire-events.sh authorized # payment.authorized (recovers)
#   ./scripts/demo/fire-events.sh captured   # payment.captured
#   ./scripts/demo/fire-events.sh duplicate  # resend the last event (dedup demo)
#   ./scripts/demo/fire-events.sh badsig     # invalid signature (rejected)
#
# Signature: HMAC-SHA256 of the RAW body with the demo webhook secret
# (Razorpay's documented algorithm). Everything is TEST-MODE / SIMULATED.
# ============================================================================
set -euo pipefail

API="${API_BASE:-http://localhost:8080}"
SECRET="${WEBHOOK_SECRET:-recoverai_demo_webhook_secret}"
EVENT_ID="${EVENT_ID:-evt_$(date +%s%N)}"
PAYMENT_ID="${PAYMENT_ID:-pay_demo_live_$(date +%s)}"

body_failed() {
  cat <<JSON
{"id":"${EVENT_ID}","event":"payment.failed","created_at":$(date +%s),"payload":{"payment":{"id":"${PAYMENT_ID}","order_id":"order_${PAYMENT_ID}","amount":349900,"currency":"INR","status":"failed","method":"card","error_code":"INSUFFICIENT_FUNDS","error_description":"The bank reported insufficient funds","failure_reason":"bank_declined","customer_id":"cust_1003","notes":{"merchant_id":"${MERCHANT_ID:-}"}}}}
JSON
}

body_authorized() {
  cat <<JSON
{"id":"${EVENT_ID}","event":"payment.authorized","created_at":$(date +%s),"payload":{"payment":{"id":"${PAYMENT_ID}","order_id":"order_${PAYMENT_ID}","amount":349900,"currency":"INR","status":"authorized","method":"card","customer_id":"cust_1003"}}}
JSON
}

body_captured() {
  cat <<JSON
{"id":"${EVENT_ID}","event":"payment.captured","created_at":$(date +%s),"payload":{"payment":{"id":"${PAYMENT_ID}","order_id":"order_${PAYMENT_ID}","amount":349900,"currency":"INR","status":"captured","method":"card","customer_id":"cust_1003"}}}
JSON
}

send() {
  local body="$1" event_type="$2"
  local sig
  sig=$(printf '%s' "$body" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')
  echo "──> $event_type  (event=${EVENT_ID}, payment=${PAYMENT_ID})"
  curl -s -X POST "${API}/api/v1/webhooks/razorpay" \
    -H "Content-Type: application/json" \
    -H "X-Razorpay-Signature: ${sig}" \
    -d "$body"
  echo
}

case "${1:-failed}" in
  failed)     send "$(body_failed)" "payment.failed" ;;
  authorized) send "$(body_authorized)" "payment.authorized" ;;
  captured)   send "$(body_captured)" "payment.captured" ;;
  duplicate)  PAYMENT_ID="${PAYMENT_ID:-pay_dup}" EVENT_ID="evt_dup_1" send "$(body_failed)" "payment.failed (duplicate)" ;;
  badsig)     curl -s -X POST "${API}/api/v1/webhooks/razorpay" -H "Content-Type: application/json" -H "X-Razorpay-Signature: deadbeef" -d "$(body_failed)"; echo ;;
  *) echo "usage: $0 {failed|authorized|captured|duplicate|badsig}" ;;
esac
