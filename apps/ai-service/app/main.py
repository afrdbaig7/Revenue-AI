"""RecoverAI AI Decision Service.

Advisory only: diagnosis, ranking, explanation, promise extraction. The service holds
NO financial state and has NO write path to the platform's payment systems. Every
response is validated against strict Pydantic schemas; any failure degrades to the
deterministic fallback engine. Prometheus metrics expose ai_request_total/failure/fallback.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import date

import prometheus_client
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, PlainTextResponse

from . import fallback
from .config import get_settings
from .prompts import DIAGNOSIS_SYSTEM, EXPLAIN_SYSTEM, PROMISE_SYSTEM, RANK_SYSTEM
from .providers import LlmProvider, LlmUnavailable
from .schemas import (
    DiagnosisRequest,
    DiagnosisResponse,
    ExplainRequest,
    ExplainResponse,
    HinglishRequest,
    HinglishResponse,
    PromiseExtractRequest,
    PromiseExtractResponse,
    RankRequest,
    RankResponse,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("recoverai.ai")

# Prometheus metrics
METRIC_REQUESTS = prometheus_client.Counter(
    "ai_request_total", "AI service requests", ["endpoint"]
)
METRIC_FAILURES = prometheus_client.Counter(
    "ai_request_failure_total", "AI service provider failures", ["endpoint"]
)
METRIC_FALLBACKS = prometheus_client.Counter(
    "ai_fallback_total", "Requests answered by the deterministic fallback", ["endpoint"]
)
METRIC_LATENCY = prometheus_client.Histogram(
    "ai_request_latency_seconds", "AI request latency", ["endpoint"]
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    log.info(
        "AI service starting provider=%s configured=%s",
        settings.llm_provider,
        settings.provider_configured,
    )
    yield


app = FastAPI(
    title="RecoverAI AI Decision Service",
    version="v1",
    description=(
        "Advisory AI for revenue recovery: structured diagnosis, strategy ranking, "
        "explanations, and promise-to-pay extraction. Deterministic fallback always "
        "available. No financial state, no write path."
    ),
    lifespan=lifespan,
)


def _provider() -> LlmProvider:
    return LlmProvider(get_settings())


def _validate_or_fallback(
    endpoint: str,
    model: type,
    system_prompt: str,
    payload: dict,
    fallback_fn,
):
    """Call the LLM, validate strictly, else fall back. The platform contract. """
    import time

    t0 = time.monotonic()
    METRIC_REQUESTS.labels(endpoint=endpoint).inc()
    try:
        raw = _provider().complete_json(system_prompt, payload)
        parsed = model.model_validate(raw)
        METRIC_LATENCY.labels(endpoint=endpoint).observe(time.monotonic() - t0)
        return parsed
    except Exception as exc:
        METRIC_FAILURES.labels(endpoint=endpoint).inc()
        METRIC_FALLBACKS.labels(endpoint=endpoint).inc()
        log.warning("LLM_FALLBACK endpoint=%s reason=%s", endpoint, exc)
        result = fallback_fn(payload)
        METRIC_LATENCY.labels(endpoint=endpoint).observe(time.monotonic() - t0)
        return result


@app.get("/healthz")
def healthz():
    settings = get_settings()
    return {
        "status": "ok",
        "provider": settings.llm_provider,
        "providerConfigured": settings.provider_configured,
        "mode": "llm" if settings.provider_configured else "deterministic-fallback",
        "promptVersion": settings.prompt_version,
    }


@app.get("/metrics")
def metrics():
    return PlainTextResponse(prometheus_client.generate_latest().decode())


@app.post("/v1/diagnose", response_model=DiagnosisResponse)
def diagnose(req: DiagnosisRequest):
    result = _validate_or_fallback(
        "diagnose",
        DiagnosisResponse,
        DIAGNOSIS_SYSTEM,
        req.model_dump(),
        lambda p: fallback.diagnose(p),
    )
    return result


@app.post("/v1/rank", response_model=RankResponse)
def rank(req: RankRequest):
    return _validate_or_fallback(
        "rank",
        RankResponse,
        RANK_SYSTEM,
        req.model_dump(),
        lambda p: fallback.rank(p),
    )


@app.post("/v1/explain", response_model=ExplainResponse)
def explain(req: ExplainRequest):
    return _validate_or_fallback(
        "explain",
        ExplainResponse,
        EXPLAIN_SYSTEM,
        req.model_dump(),
        lambda p: fallback.explain(p),
    )


@app.post("/v1/promise/extract", response_model=PromiseExtractResponse)
def extract_promise(req: PromiseExtractRequest):
    payload = {"text": req.text, "today": date.today().isoformat()}
    system_prompt = PROMISE_SYSTEM.replace("{today}", date.today().isoformat())
    return _validate_or_fallback(
        "promise_extract",
        PromiseExtractResponse,
        system_prompt,
        payload,
        lambda p: fallback.extract_promise(p["text"]),
    )


@app.post("/v1/communication/hinglish", response_model=HinglishResponse)
def hinglish(req: HinglishRequest):
    """Hinglish conversational recovery message (P3, text-first, voice-ready).

    Deterministic templates enforce safety (no threats, no false urgency, no legal
    claims). When an LLM is configured, the template is still the floor — the model
    may only rephrase within the same intent and is schema-validated on the way out.
    """
    return _validate_or_fallback(
        "hinglish",
        HinglishResponse,
        "You rephrase a Hinglish payment reminder. Keep it polite, short, no threats, "
        "no legal claims, no urgency fabrication. Return JSON {\"message\": \"...\"} only.",
        req.model_dump(),
        lambda p: fallback.hinglish_message(p),
    )


@app.exception_handler(Exception)
async def unhandled(_: Request, exc: Exception):
    log.exception("unhandled error: %s", exc)
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "internal error"})
