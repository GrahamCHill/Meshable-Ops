FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /worker
COPY workers/entry.py /worker/entry.py
COPY sdk/python/meshable.py /worker/sdk/meshable.py
USER 10001:10001
ENTRYPOINT ["python", "-I", "/worker/entry.py"]
