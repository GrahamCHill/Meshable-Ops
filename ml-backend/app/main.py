from hashlib import blake2b
from importlib import import_module
from platform import python_version
import re
from typing import Any

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field


app = FastAPI(
    title="Superset ML Backend",
    version="1.0.0",
    description="Small Python service for ML-friendly workflows and package-backed helpers."
)


class VectorizeRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, max_length=128)
    dimensions: int = Field(8, ge=1, le=4096)


class PackageStatus(BaseModel):
    package: str
    installed: bool
    version: str | None = None
    error: str | None = None


def package_status(package_name: str) -> PackageStatus:
    try:
        module = import_module(package_name)
        return PackageStatus(
            package=package_name,
            installed=True,
            version=getattr(module, "__version__", None),
        )
    except Exception as exc:  # pragma: no cover - simple runtime probe
        return PackageStatus(package=package_name, installed=False, error=str(exc))


TOKEN_PATTERN = re.compile(r"[a-z0-9]+(?:['-][a-z0-9]+)?")
COMMON_STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if",
    "in", "into", "is", "it", "no", "not", "of", "on", "or", "s", "such", "t",
    "that", "the", "their", "then", "there", "these", "they", "this", "to", "was",
    "will", "with",
}


def normalize_token(token: str) -> str:
    token = token.lower().strip("' -")
    if len(token) > 4:
        for suffix in ("ingly", "edly", "ing", "ed", "ies", "es", "s"):
            if token.endswith(suffix) and len(token) - len(suffix) >= 3:
                if suffix == "ies":
                    return token[:-3] + "y"
                return token[:-len(suffix)]
    return token


def tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    for match in TOKEN_PATTERN.finditer(text.lower()):
        token = normalize_token(match.group(0))
        if token and token not in COMMON_STOPWORDS:
            tokens.append(token)
    return tokens


def stable_feature_index(feature: str, dimensions: int) -> tuple[int, float]:
    digest = blake2b(feature.encode("utf-8"), digest_size=8).digest()
    value = int.from_bytes(digest, "big", signed=False)
    index = value % dimensions
    sign = 1.0 if ((value >> 63) & 1) == 0 else -1.0
    return index, sign


def build_features(tokens: list[str]) -> list[tuple[str, float]]:
    counts: dict[str, float] = {}

    for token in tokens:
        counts[f"tok:{token}"] = counts.get(f"tok:{token}", 0.0) + 1.0
        if len(token) >= 5:
            counts[f"prefix:{token[:4]}"] = counts.get(f"prefix:{token[:4]}", 0.0) + 0.4
        if len(token) >= 6:
            counts[f"suffix:{token[-4:]}"] = counts.get(f"suffix:{token[-4:]}", 0.0) + 0.3
        if len(token) >= 4:
            for idx in range(len(token) - 2):
                trigram = token[idx:idx + 3]
                counts[f"tri:{trigram}"] = counts.get(f"tri:{trigram}", 0.0) + 0.15

    for left, right in zip(tokens, tokens[1:]):
        counts[f"bi:{left}_{right}"] = counts.get(f"bi:{left}_{right}", 0.0) + 0.75

    return [(feature, 1.0 + np.log1p(weight)) for feature, weight in counts.items()]


def vectorize_text(text: str, dimensions: int) -> list[float]:
    tokens = tokenize(text)
    if not tokens:
        tokens = ["empty"]

    values = np.zeros(dimensions, dtype=np.float32)
    for feature, weight in build_features(tokens):
        index, sign = stable_feature_index(feature, dimensions)
        values[index] += np.float32(weight * sign)

    norm = np.linalg.norm(values)
    if norm > 0:
        values /= norm
    return values.tolist()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "python": python_version(),
        "packages": {
            "numpy": package_status("numpy").model_dump(),
            "paddle": package_status("paddle").model_dump(),
        },
    }


@app.get("/api/v1/ml/runtime")
def runtime() -> dict[str, Any]:
    return {
        "service": "ml-backend",
        "python": python_version(),
        "packages": {
            "numpy": package_status("numpy").model_dump(),
            "paddle": package_status("paddle").model_dump(),
        },
    }


@app.get("/api/v1/ml/packages/{package_name}")
def package_probe(package_name: str) -> PackageStatus:
    return package_status(package_name)


@app.post("/api/v1/ml/vectorize")
def vectorize(request: VectorizeRequest) -> dict[str, Any]:
    vectors = [vectorize_text(text, request.dimensions) for text in request.texts]

    return {
        "dimensions": request.dimensions,
        "count": len(vectors),
        "vectors": vectors,
    }


@app.get("/api/v1/ml/paddle/check")
def paddle_check() -> dict[str, Any]:
    status = package_status("paddle")
    return {
        "available": status.installed,
        "version": status.version,
        "error": status.error,
    }
