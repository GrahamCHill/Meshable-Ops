FROM python:3.12-slim
WORKDIR /runner
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.lock .
RUN pip install --require-hashes --only-binary=:all: --no-cache-dir -r requirements.lock \
    && groupadd --gid 10001 runner \
    && useradd --uid 10001 --gid runner --no-create-home runner
COPY app.py execution.py ./
USER runner
EXPOSE 8090
CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090", "--no-access-log"]
