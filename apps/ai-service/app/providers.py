"""Provider-agnostic LLM adapter: OpenAI, Gemini, Groq — all via plain HTTP.

Every provider call is wrapped with: strict timeout, bounded retries (tenacity),
JSON output contract, and validation against the Pydantic schemas. Repeated provider
failure opens a circuit breaker; the service then answers from the deterministic
engine so the platform never blocks on the LLM.
"""
from __future__ import annotations

import json
import time
from typing import Any, Optional

import httpx
from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from .config import Settings


class LlmUnavailable(Exception):
    """Raised when the configured LLM provider cannot answer."""


class CircuitBreaker:
    def __init__(self, failure_threshold: int, cooldown_seconds: float) -> None:
        self.failure_threshold = failure_threshold
        self.cooldown_seconds = cooldown_seconds
        self.failures = 0
        self.opened_at: Optional[float] = None

    def allow(self) -> bool:
        if self.opened_at is None:
            return True
        if time.monotonic() - self.opened_at > self.cooldown_seconds:
            self.opened_at = None
            self.failures = 0
            return True
        return False

    def record_success(self) -> None:
        self.failures = 0

    def record_failure(self) -> None:
        self.failures += 1
        if self.failures >= self.failure_threshold:
            self.opened_at = time.monotonic()


class LlmProvider:
    """Adapter for OpenAI-compatible chat endpoints (OpenAI, Groq) and Gemini."""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.breaker = CircuitBreaker(
            settings.circuit_breaker_failures, settings.circuit_breaker_cooldown_seconds
        )
        self.client = httpx.Client(timeout=settings.llm_timeout_seconds)

    def complete_json(self, system_prompt: str, user_payload: dict[str, Any]) -> dict[str, Any]:
        if not self.settings.provider_configured:
            raise LlmUnavailable("LLM provider not configured")
        if not self.breaker.allow():
            raise LlmUnavailable("circuit open")
        try:
            text = self._call(system_prompt, user_payload)
            data = json.loads(text)
            if not isinstance(data, dict):
                raise LlmUnavailable("non-object JSON from provider")
            self.breaker.record_success()
            return data
        except (httpx.HTTPError, json.JSONDecodeError, KeyError, LlmUnavailable) as exc:
            self.breaker.record_failure()
            if isinstance(exc, LlmUnavailable):
                raise
            raise LlmUnavailable(f"provider failure: {exc}") from exc

    def _call(self, system_prompt: str, user_payload: dict[str, Any]) -> str:
        provider = self.settings.llm_provider
        if provider == "gemini":
            return self._call_gemini(system_prompt, user_payload)
        return self._call_openai_compatible(system_prompt, user_payload)

    @retry(
        retry=retry_if_exception_type(httpx.HTTPError),
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=0.5, max=4),
    )
    def _call_openai_compatible(self, system_prompt: str, user_payload: dict[str, Any]) -> str:
        settings = self.settings
        if settings.llm_provider == "groq":
            url = "https://api.groq.com/openai/v1/chat/completions"
            api_key = settings.groq_api_key
            model = settings.groq_model
        else:
            url = "https://api.openai.com/v1/chat/completions"
            api_key = settings.openai_api_key
            model = settings.openai_model

        response = self.client.post(
            url,
            headers={"Authorization": f"Bearer {api_key}"},
            json={
                "model": model,
                "temperature": settings.llm_temperature,
                "response_format": {"type": "json_object"},
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": json.dumps(user_payload)},
                ],
            },
        )
        response.raise_for_status()
        return response.json()["choices"][0]["message"]["content"]

    @retry(
        retry=retry_if_exception_type(httpx.HTTPError),
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=0.5, max=4),
    )
    def _call_gemini(self, system_prompt: str, user_payload: dict[str, Any]) -> str:
        settings = self.settings
        url = (
            f"https://generativelanguage.googleapis.com/v1beta/models/"
            f"{settings.gemini_model}:generateContent?key={settings.gemini_api_key}"
        )
        response = self.client.post(
            url,
            json={
                "system_instruction": {"parts": [{"text": system_prompt}]},
                "contents": [{"role": "user", "parts": [{"text": json.dumps(user_payload)}]}],
                "generationConfig": {
                    "temperature": settings.llm_temperature,
                    "responseMimeType": "application/json",
                },
            },
        )
        response.raise_for_status()
        return response.json()["candidates"][0]["content"]["parts"][0]["text"]
