"""Deterministic fallback engine — the AI service's floor.

When no LLM is configured, the provider fails, the circuit is open, or the model's
output fails schema validation, these rule-based answers are returned. They mirror
the platform's own deterministic classification so the two always agree.
"""
from __future__ import annotations

from datetime import date, timedelta

from .schemas import (
    DiagnosisResponse,
    ExplainResponse,
    HinglishResponse,
    PromiseExtractResponse,
    RankResponse,
)

_FALLBACK_CONFIDENCE = {
    "INSUFFICIENT_FUNDS": 0.82,
    "CARD_EXPIRED": 0.95,
    "CARD_BLOCKED": 0.85,
    "MANDATE_CANCELLED": 0.90,
    "MANDATE_FAILURE": 0.80,
    "NETWORK_TIMEOUT": 0.70,
    "AUTHENTICATION_FAILURE": 0.88,
    "CHECKOUT_ABANDONED": 0.90,
    "BANK_DECLINE": 0.60,
    "PROCESSOR_ERROR": 0.60,
    "CUSTOMER_ABORTED": 0.60,
    "UNKNOWN": 0.45,
}

_CODE_HINTS = {
    "INSUFFICIENT": "INSUFFICIENT_FUNDS",
    "FUNDS": "INSUFFICIENT_FUNDS",
    "EXPIRED": "CARD_EXPIRED",
    "BLOCKED": "CARD_BLOCKED",
    "FROZEN": "CARD_BLOCKED",
    "TIMEOUT": "NETWORK_TIMEOUT",
    "DECLINED": "BANK_DECLINE",
    "DECLINE": "BANK_DECLINE",
    "MANDATE": "MANDATE_FAILURE",
    "OTP": "AUTHENTICATION_FAILURE",
    "AUTH": "AUTHENTICATION_FAILURE",
    "ABORT": "CUSTOMER_ABORTED",
    "CANCELLED": "CUSTOMER_ABORTED",
}


def diagnose(payload: dict) -> DiagnosisResponse:
    code = (payload.get("providerCode") or "").upper()
    hint = payload.get("failureCategoryHint")
    category = "UNKNOWN"
    for token, mapped in _CODE_HINTS.items():
        if token in code:
            category = mapped
            break
    if category == "UNKNOWN" and hint and hint != "UNKNOWN":
        category = hint

    if category in ("CARD_EXPIRED", "CARD_BLOCKED", "MANDATE_CANCELLED", "MANDATE_FAILURE", "AUTHENTICATION_FAILURE"):
        step = "PAYMENT_LINK"
    elif category in ("INSUFFICIENT_FUNDS", "NETWORK_TIMEOUT", "PROCESSOR_ERROR"):
        step = "DELAYED_RETRY"
    elif category == "CHECKOUT_ABANDONED":
        step = "EMAIL_NUDGE"
    elif category == "BANK_DECLINE":
        step = "ALTERNATE_PAYMENT_METHOD"
    else:
        step = "NO_ACTION"

    return DiagnosisResponse(
        failureCategory=category,
        confidence=_FALLBACK_CONFIDENCE.get(category, 0.45),
        evidence=["provider_code_mapping", "deterministic_fallback"],
        recommendedNextStep=step,
        modelVersion="recoverai-deterministic-v1",
        promptVersion="recoverai-prompts-v1",
    )


def rank(payload: dict) -> RankResponse:
    candidates = payload.get("candidates", [])
    ordered = sorted(candidates, key=lambda c: c.get("expectedValueMinor", 0), reverse=True)
    ranking = [{"strategy": c["strategy"], "rank": i + 1} for i, c in enumerate(ordered)]
    return RankResponse(
        ranking=ranking,
        explanation="Deterministic ranking by expected value (fallback engine).",
    )


def explain(payload: dict) -> ExplainResponse:
    incident = payload.get("incident", {})
    diagnosis = payload.get("diagnosis", {})
    decision = payload.get("decision", {})
    summary = (
        f"Payment of {incident.get('amountMinor', 0)} {incident.get('currency', 'INR')} failed with "
        f"{diagnosis.get('failureCategory', 'UNKNOWN')}; selected {decision.get('chosenStrategy', 'NO_ACTION')}."
    )
    return ExplainResponse(
        summary=summary,
        rationale="Deterministic fallback explanation: strategy ranked highest by expected value "
        "under the policy engine's constraints.",
    )


def hinglish_message(req: dict) -> HinglishResponse:
    """Constrained Hinglish templates — no threats, no false urgency, no legal claims.

    Voice-ready: the returned string is designed to be spoken by a TTS engine; the
    platform decides channel/opt-out/cooldown, never the model.
    """
    name = req.get("customerName") or "ji"
    amount = int(req.get("amountMinor", 0)) / 100
    merchant = req.get("merchantName") or "aapka merchant"
    intent = req.get("intent", "payment_link")

    if intent == "payment_link":
        message = (
            f"Hi {name}, aapka {amount:.0f} rupee ka payment {merchant} ka complete nahi ho paya. "
            "Kya main aapko ek secure payment link bhej doon? Aap kabhi bhi complete kar sakte hain."
        )
    elif intent == "retry":
        message = (
            f"Hi {name}, {merchant} ka {amount:.0f} rupee payment attempt fail ho gaya tha. "
            "Hum kal dobara try karenge — koi action nahi chahiye aapko."
        )
    elif intent == "promise_reminder":
        message = (
            f"Hi {name}, aapke wade ke mutabik aaj ka din hai. "
            f"Secure link se {amount:.0f} rupee ka payment complete kar dijiye, dhanyavaad!"
        )
    elif intent == "payment_method_update":
        message = (
            f"Hi {name}, {merchant} ke liye aapka saved payment method kaam nahi kar raha. "
            "Naya method add karne se service continue rahegi."
        )
    else:  # discount
        message = (
            f"Hi {name}, {amount:.0f} rupee ka payment complete karne par ek chhota sa credit "
            f"{merchant} ki taraf se milega. Secure link bhej doon?"
        )
    return HinglishResponse(
        message=message,
        channel="WHATSAPP",
        modelVersion="recoverai-deterministic-v1",
        promptVersion="recoverai-prompts-v1",
    )


def extract_promise(text: str) -> PromiseExtractResponse:
    lower = text.lower()
    today = date.today()
    target = today + timedelta(days=1)
    if "monday" in lower:
        target = today + timedelta(days=(7 - today.weekday()) % 7 or 7)
    elif "tomorrow" in lower:
        target = today + timedelta(days=1)
    elif "1st" in lower or "first" in lower:
        target = today.replace(day=1) + timedelta(days=32)
        target = target.replace(day=1)
    time_text = "morning" if "morning" in lower else "afternoon" if "afternoon" in lower else "evening"
    hour = {"morning": 10, "afternoon": 15, "evening": 18}[time_text]
    return PromiseExtractResponse(
        promisedDate=target.isoformat(),
        preferredTimeText=time_text,
        preferredTime={"hour": hour, "minute": 30 if hour != 15 else 0},
        channel="WHATSAPP",
        confidence=0.65,
    )
