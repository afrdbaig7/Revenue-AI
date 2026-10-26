import time
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import Mock

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import app, _provider
from app.providers import CircuitBreaker, LlmProvider, LlmUnavailable
from app.schemas import PromiseExtractResponse, RankRequest


def test_provider_is_shared_between_requests():
    assert _provider() is _provider()


def test_open_breaker_allows_only_one_concurrent_probe():
    breaker = CircuitBreaker(1, 0)
    breaker.record_failure()
    breaker.opened_at = time.monotonic() - 1
    with ThreadPoolExecutor(max_workers=8) as executor:
        allowed = list(executor.map(lambda _: breaker.allow(), range(8)))
    assert sum(allowed) == 1
    breaker.record_success()
    assert breaker.allow()


def test_authentication_failure_is_not_retried():
    provider = LlmProvider(Settings(llm_provider="openai", openai_api_key="test", llm_max_retries=4))
    request = httpx.Request("POST", "https://example.test")
    response = httpx.Response(401, request=request)
    provider.client.post = Mock(side_effect=httpx.HTTPStatusError("Unauthorized", request=request, response=response))
    try:
        with pytest.raises(LlmUnavailable):
            provider.complete_json("system", {})
        assert provider.client.post.call_count == 1
    finally:
        provider.close()


def test_rejects_duplicate_or_unknown_candidates():
    for candidates in [[{"strategy": "PAYMENT_LINK"}] * 2, [{"strategy": "INVENTED"}]]:
        with pytest.raises(ValueError):
            RankRequest(candidates=candidates)


def test_promise_outputs_validate_calendar_and_clock():
    for values in [{"promisedDate": "not-a-date"}, {"promisedDate": "2026-10-26", "preferredTime": {"hour": 25}}]:
        with pytest.raises(ValueError):
            PromiseExtractResponse(confidence=0.8, **values)


def test_large_request_is_rejected_before_provider_processing():
    client = TestClient(app)
    response = client.post("/v1/diagnose", content=b"x" * 70_000)
    assert response.status_code == 413


def test_duplicate_model_ranking_falls_back_to_eligible_candidates(monkeypatch):
    import app.main as main
    fake = Mock()
    fake.complete_json.return_value = {"ranking": [{"strategy": "PAYMENT_LINK", "rank": 1}] * 2}
    monkeypatch.setattr(main, "_provider", lambda: fake)
    response = TestClient(app).post("/v1/rank", json={"candidates": [
        {"strategy": "PAYMENT_LINK", "expectedValueMinor": 200}, {"strategy": "NO_ACTION", "expectedValueMinor": 0}
    ]})
    assert response.status_code == 200
    assert [entry["strategy"] for entry in response.json()["ranking"]] == ["PAYMENT_LINK", "NO_ACTION"]
