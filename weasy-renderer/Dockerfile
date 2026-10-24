FROM python:3.12-slim
WORKDIR /renderer
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 XDG_CACHE_HOME=/tmp/cache
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpango-1.0-0 libpangoft2-1.0-0 libharfbuzz-subset0 fonts-liberation \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.lock .
RUN pip install --require-hashes --only-binary=:all: --no-cache-dir -r requirements.lock && useradd --create-home renderer
COPY backend ./backend
COPY plugins ./plugins
COPY app.py worker.py report.py report_cover.py resources.py ./
USER renderer
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
