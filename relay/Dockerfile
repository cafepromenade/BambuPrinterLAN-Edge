FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Non-root
RUN useradd -m relay && chown -R relay:relay /app
USER relay

EXPOSE 8979

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import urllib.request,sys; urllib.request.urlopen('http://127.0.0.1:8979/healthz'); " || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8979"]
