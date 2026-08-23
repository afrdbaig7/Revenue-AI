# ============================================================================
# RecoverAI — developer workflow
# ============================================================================
SHELL := /bin/bash
API_DIR := apps/api
AI_DIR := apps/ai-service
WEB_DIR := apps/web

.PHONY: help setup infra-up infra-down dev api ai web migrate seed test test-unit test-it lint experiment bench clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## One-time setup: copy env template, install deps
	@cp -n .env.example .env || true
	@cd $(WEB_DIR) && npm install
	@cd $(AI_DIR) && python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
	@echo "Setup complete. Edit .env and run: make infra-up && make migrate && make seed"

infra-up: ## Start infrastructure (Postgres, Redis, Redpanda, Temporal, Grafana)
	docker compose up -d postgres redis redpanda temporal temporal-ui prometheus grafana

infra-down: ## Stop infrastructure
	docker compose down

dev: ## Run all services locally (needs infra-up first)
	@echo "Starting API on :8080, AI service on :8100, Web on :3000"
	@cd $(API_DIR) && mvn -q spring-boot:run -Dspring-boot.run.profiles=local &

api: ## Run the Spring Boot API only
	cd $(API_DIR) && mvn spring-boot:run -Dspring-boot.run.profiles=local

ai: ## Run the AI decision service only
	cd $(AI_DIR) && .venv/bin/uvicorn app.main:app --reload --port 8100

web: ## Run the Next.js dashboard only
	cd $(WEB_DIR) && npm run dev

migrate: ## Apply Flyway migrations
	cd $(API_DIR) && mvn -q flyway:migrate

seed: ## Seed demo data (demo merchant, incidents, metrics)
	cd $(API_DIR) && mvn -q exec:java -Dexec.mainClass=com.recoverai.tools.SeederMain -Dexec.args="seed"

seed-reset: ## Drop + recreate schema, then seed
	cd $(API_DIR) && mvn -q flyway:clean flyway:migrate
	cd $(API_DIR) && mvn -q exec:java -Dexec.mainClass=com.recoverai.tools.SeederMain -Dexec.args="seed"

test: ## Run all unit tests (API + AI service)
	cd $(API_DIR) && mvn -q test
	cd $(AI_DIR) && .venv/bin/pytest -q

test-unit: ## Backend unit tests only
	cd $(API_DIR) && mvn -q test -Dtest='*Test' -DfailIfNoTests=false

test-it: ## Backend integration tests (requires Docker)
	cd $(API_DIR) && mvn -q verify -Dtest='*IT' -DfailIfNoTests=false

lint: ## Lint backend + frontend + AI service
	cd $(API_DIR) && mvn -q spotless:check
	cd $(WEB_DIR) && npm run lint
	cd $(AI_DIR) && .venv/bin/ruff check .

experiment: ## Run a batch experiment (baseline vs RecoverAI) and print report
	cd $(API_DIR) && mvn -q exec:java -Dexec.mainClass=com.recoverai.tools.ExperimentMain

bench: ## k6 load test (requires k6 installed)
	k6 run scripts/load/webhook-burst.js

clean: ## Remove build artifacts and generated data
	cd $(API_DIR) && mvn -q clean
	cd $(WEB_DIR) && rm -rf .next
	cd $(AI_DIR) && rm -rf .pytest_cache
	rm -rf reports tmp
