"""Provider-agnostic LLM adapter: OpenAI, Gemini, Groq — all via plain HTTP.

Every provider call is wrapped with: strict timeout, bounded retries (tenacity),
JSON output contract, and validation against the Pydantic schemas. Repeated provider
failure opens a circuit breaker; the service then answers from the deterministic
engine so the platform never blocks on the LLM.
"""
from __future__ import annotations

import json
import time
import threading
from typing import Any, Optional

import httpx

from .config import Settings


class LlmUnavailable(Exception):
    """Raised when the configured LLM provider cannot answer."""


class CircuitBreaker:
    def __init__(self, failure_threshold: int, cooldown_seconds: float) -> None:
        self.failure_threshold = failure_threshold
        self.cooldown_seconds = cooldown_seconds
        self.failures = 0
        self.opened_at: Optional[float] = None
        self._lock = threading.Lock()
        self._probing = False

    def allow(self) -> bool:
        with self._lock:
            if self.opened_at is None:
                return True
            if time.monotonic() - self.opened_at > self.cooldown_seconds and not self._probing:
                self._probing = True
                return True
            return False

    def record_success(self) -> None:
        with self._lock:
            self.failures = 0
            self.opened_at = None
            self._probing = False

    def record_failure(self) -> None:
        with self._lock:
            self.failures += 1
            self._probing = False
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
        self._slots = threading.BoundedSemaphore(settings.max_concurrent_requests)

    def close(self) -> None:
        self.client.close()

    def complete_json(self, system_prompt: str, user_payload: dict[str, Any]) -> dict[str, Any]:
        if not self.settings.provider_configured:
            raise LlmUnavailable("LLM provider not configured")
        if not self._slots.acquire(blocking=False):
            raise LlmUnavailable("capacity reached")
        if not self.breaker.allow():
            self._slots.release()
            raise LlmUnavailable("circuit open")
        try:
            deadline = time.monotonic() + self.settings.llm_timeout_seconds
            text = ""
            for attempt in range(self.settings.llm_max_retries + 1):
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise LlmUnavailable("deadline exceeded")
                try:
                    text = self._call(system_prompt, user_payload, min(remaining / 4, 1.5))
                    break
                except httpx.HTTPError as exc:
                    retryable = not isinstance(exc, httpx.HTTPStatusError) or exc.response.status_code in {429, 500, 502, 503, 504}
                    if not retryable or attempt >= self.settings.llm_max_retries:
                        raise
                    delay = min(0.1 * 2 ** attempt, max(0, deadline - time.monotonic()))
                    time.sleep(delay)
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
        finally:
            self._slots.release()

    def _call(self, system_prompt: str, user_payload: dict[str, Any], timeout: float) -> str:
        provider = self.settings.llm_provider
        if provider == "gemini":
            return self._call_gemini(system_prompt, user_payload, timeout)
        return self._call_openai_compatible(system_prompt, user_payload, timeout)

    def _call_openai_compatible(self, system_prompt: str, user_payload: dict[str, Any], timeout: float) -> str:
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
            timeout=timeout,
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

    def _call_gemini(self, system_prompt: str, user_payload: dict[str, Any], timeout: float) -> str:
        settings = self.settings
        url = (
            f"https://generativelanguage.googleapis.com/v1beta/models/"
            f"{settings.gemini_model}:generateContent?key={settings.gemini_api_key}"
        )
        response = self.client.post(
            url,
            timeout=timeout,
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
