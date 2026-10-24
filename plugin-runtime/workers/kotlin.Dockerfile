FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache python3
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /worker
COPY workers/entry.py /worker/entry.py
USER 10001:10001
ENTRYPOINT ["python3", "-I", "/worker/entry.py"]
