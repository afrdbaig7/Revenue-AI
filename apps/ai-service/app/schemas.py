"""Strict, bounded Pydantic schemas for every AI output.

All LLM output is validated against these schemas before it leaves the service.
Anything that does not validate is discarded and the deterministic engine's result
is returned instead. These schemas are the contract with the platform.
"""
from __future__ import annotations

from typing import Literal, Optional
from datetime import date

from pydantic import BaseModel, Field, field_validator

FailureCategory = Literal[
    "INSUFFICIENT_FUNDS",
    "CARD_EXPIRED",
    "CARD_BLOCKED",
    "BANK_DECLINE",
    "MANDATE_CANCELLED",
    "MANDATE_FAILURE",
    "NETWORK_TIMEOUT",
    "PROCESSOR_ERROR",
    "AUTHENTICATION_FAILURE",
    "CUSTOMER_ABORTED",
    "CHECKOUT_ABANDONED",
    "UNKNOWN",
]

NextStep = Literal[
    "DELAYED_RETRY",
    "PAYMENT_LINK",
    "ALTERNATE_PAYMENT_METHOD",
    "EMAIL_NUDGE",
    "WAIT_FOR_PROVIDER_RETRY",
    "PROMISE_TO_PAY",
    "NO_ACTION",
]


class DiagnosisRequest(BaseModel):
    """Bounded context sent to the model. No PII, no secrets, no card data."""

    failureCategoryHint: Optional[str] = None
    providerCode: Optional[str] = Field(default=None, max_length=64)
    providerReason: Optional[str] = Field(default=None, max_length=255)
    paymentMethod: Optional[str] = Field(default=None, max_length=32)
    amountMinor: int = Field(ge=0)
    currency: str = Field(default="INR", max_length=3)
    incidentType: str = Field(default="PAYMENT_FAILURE", max_length=32)
    customerHistory: dict = Field(default_factory=dict)

    @field_validator("customerHistory")
    @classmethod
    def _bound_history(cls, v: dict) -> dict:
        allowed = {"previousFailures", "previousSuccesses", "recoveryHistory", "segment"}
        return {k: v[k] for k in allowed if k in v}


class DiagnosisResponse(BaseModel):
    failureCategory: FailureCategory
    confidence: float = Field(ge=0.0, le=1.0)
    evidence: list[str] = Field(default_factory=list, max_length=8)
    recommendedNextStep: NextStep
    modelVersion: str = "unknown"
    promptVersion: str = "unknown"


Strategy = Literal["NO_ACTION", "PAYMENT_LINK", "UPI_RECOVERY", "DELAYED_RETRY", "EMAIL_NUDGE", "WHATSAPP_NUDGE", "SMS_NUDGE", "BOUNDED_DISCOUNT", "ALTERNATE_PAYMENT_METHOD", "WAIT_FOR_PROVIDER_RETRY", "PROMISE_TO_PAY", "MANUAL_ESCALATION"]


class Candidate(BaseModel):
    strategy: Strategy
    expectedValueMinor: int = Field(ge=0, default=0)
    probability: float = Field(ge=0, le=1, default=0)
    expectedGrossMinor: int = Field(ge=0, default=0)
    interventionCostMinor: int = Field(ge=0, default=0)
    discountCostMinor: int = Field(ge=0, default=0)
    riskPenaltyMinor: int = Field(ge=0, default=0)
    frictionPenaltyMinor: int = Field(ge=0, default=0)
    timeToRecoveryHours: float = Field(ge=0, le=720, default=0)
    rationale: str = Field(max_length=500, default="")


class RankedStrategy(BaseModel):
    strategy: Strategy
    rank: int = Field(ge=1, le=16)


class RankRequest(BaseModel):
    candidates: list[Candidate] = Field(min_length=1, max_length=16)

    @field_validator("candidates")
    @classmethod
    def unique_candidates(cls, value):
        if len({item.strategy for item in value}) != len(value):
            raise ValueError("Duplicate candidate")
        return value


class RankResponse(BaseModel):
    ranking: list[RankedStrategy] = Field(min_length=1, max_length=16)
    explanation: Optional[str] = Field(default=None, max_length=500)


class ExplainRequest(BaseModel):
    incident: dict = Field(default_factory=dict)
    diagnosis: dict = Field(default_factory=dict)
    decision: dict = Field(default_factory=dict)


class ExplainResponse(BaseModel):
    summary: str = Field(max_length=300)
    rationale: str = Field(max_length=500)


class PromiseExtractRequest(BaseModel):
    text: str = Field(min_length=2, max_length=300)


class PreferredTime(BaseModel):
    hour: int = Field(ge=0, le=23, default=18)
    minute: int = Field(ge=0, le=59, default=30)


class PromiseExtractResponse(BaseModel):
    promisedDate: str
    preferredTimeText: str = Field(default="evening", max_length=16)
    preferredTime: PreferredTime = Field(default_factory=PreferredTime)
    channel: Literal["EMAIL", "SMS", "WHATSAPP", "DEMO_INBOX"] = "WHATSAPP"
    confidence: float = Field(ge=0.0, le=1.0)

    @field_validator("promisedDate")
    @classmethod
    def valid_date(cls, value: str) -> str:
        return date.fromisoformat(value).isoformat()


class HinglishRequest(BaseModel):
    """Bounded context for Hinglish message generation. No PII beyond the customer's
    own name; no secrets; amount as minor units."""

    customerName: str = Field(default="", max_length=60)
    amountMinor: int = Field(ge=0)
    currency: str = Field(default="INR", max_length=3)
    merchantName: str = Field(default="", max_length=80)
    intent: str = Field(
        default="payment_link",
        pattern="^(payment_link|retry|promise_reminder|payment_method_update|discount)$",
    )


class HinglishResponse(BaseModel):
    message: str = Field(max_length=280)
    channel: str = Field(default="WHATSAPP", max_length=16)
    modelVersion: str = "unknown"
    promptVersion: str = "unknown"
