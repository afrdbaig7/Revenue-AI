# ============================================================================
# RecoverAI AI decision service — multi-stage production container
# ============================================================================
FROM python:3.12-slim AS build
WORKDIR /build
COPY apps/ai-service/requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.12-slim
RUN useradd -m -u 1001 recoverai
USER recoverai
WORKDIR /app
ENV PATH="/install/bin:$PATH" PYTHONPATH="/install/lib/python3.12/site-packages:/app"
COPY --from=build /install /install
COPY apps/ai-service/app ./app
EXPOSE 8100
HEALTHCHECK --interval=10s --timeout=3s --retries=6 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8100/healthz', timeout=3).status==200 else 1)"
ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
