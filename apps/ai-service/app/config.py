"""RecoverAI AI decision service configuration.

All settings come from environment variables (see .env.example). The service MUST
function without any LLM credentials: providers degrade to the deterministic engine.
"""
from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", env_file=".env", extra="ignore")

    service_name: str = "recoverai-ai"
    llm_provider: str = "none"  # openai | gemini | groq | none
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.0-flash"
    groq_api_key: str = ""
    groq_model: str = "llama-3.3-70b-versatile"

    llm_timeout_seconds: float = 8.0
    llm_max_retries: int = 2
    llm_temperature: float = 0.1
    circuit_breaker_failures: int = 5
    circuit_breaker_cooldown_seconds: float = 30.0

    prompt_version: str = "recoverai-prompts-v1"
    model_version: str = "recoverai-v1"

    @property
    def provider_configured(self) -> bool:
        if self.llm_provider == "openai":
            return bool(self.openai_api_key)
        if self.llm_provider == "gemini":
            return bool(self.gemini_api_key)
        if self.llm_provider == "groq":
            return bool(self.groq_api_key)
        return False


@lru_cache
def get_settings() -> Settings:
    return Settings()
