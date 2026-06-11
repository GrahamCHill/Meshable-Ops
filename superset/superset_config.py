import os
from flask_caching.backends.rediscache import RedisCache

# ─── Core ─────────────────────────────────────────────────────────────────────
SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "change_me_in_production")
SQLALCHEMY_DATABASE_URI = (
    f"postgresql+psycopg2://"
    f"{os.environ.get('DATABASE_USER', 'superset_user')}:"
    f"{os.environ.get('DATABASE_PASSWORD', 'superset_pass')}@"
    f"{os.environ.get('DATABASE_HOST', 'postgres')}:"
    f"{os.environ.get('DATABASE_PORT', '5432')}/"
    f"{os.environ.get('DATABASE_DB', 'superset')}"
)

# ─── Redis cache + Celery ─────────────────────────────────────────────────────
REDIS_HOST = os.environ.get("REDIS_HOST", "redis")
REDIS_PORT = int(os.environ.get("REDIS_PORT", 6379))
REDIS_URL = f"redis://{REDIS_HOST}:{REDIS_PORT}/0"

CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_",
    "CACHE_REDIS_URL": REDIS_URL,
}

DATA_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 3600,
    "CACHE_KEY_PREFIX": "superset_data_",
    "CACHE_REDIS_URL": REDIS_URL,
}

FILTER_STATE_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 3600,
    "CACHE_KEY_PREFIX": "superset_filter_",
    "CACHE_REDIS_URL": REDIS_URL,
}

EXPLORE_FORM_DATA_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 3600,
    "CACHE_KEY_PREFIX": "superset_explore_",
    "CACHE_REDIS_URL": REDIS_URL,
}

class CeleryConfig:
    broker_url = REDIS_URL
    imports = (
        "superset.sql_lab",
        "superset.tasks.scheduler",
        "superset.tasks.thumbnails",
        "superset.tasks.cache",
    )
    result_backend = REDIS_URL
    worker_prefetch_multiplier = 10
    task_acks_late = True
    task_annotations = {
        "sql_lab.get_sql_results": {"rate_limit": "100/s"},
    }

CELERY_CONFIG = CeleryConfig

# ─── Embedding / iFrame ───────────────────────────────────────────────────────
# Allow Superset to be embedded in your React frontend
FEATURE_FLAGS = {
    "EMBEDDED_SUPERSET": True,
    "EMBEDDABLE_CHARTS": True,
    "DASHBOARD_CROSS_FILTERS": True,
    "DASHBOARD_FILTERS_EXPERIMENTAL": True,
    "ENABLE_TEMPLATE_PROCESSING": True,
    "ALERT_REPORTS": True,
}

# Allow iframes from your frontend origin
TALISMAN_ENABLED = False                     # Disable strict CSP for dev
WTF_CSRF_ENABLED = False
WTF_CSRF_EXEMPT_LIST = ["superset.views.core.log"]

# Embedding: origins that are allowed to embed Superset dashboards
ALLOWED_ORIGINS = [
    "http://localhost:3000",
    "http://localhost:5173",
    "http://frontend:80",
]

# HTTP headers required for cross-origin iframe embedding
HTTP_HEADERS = {
    "X-Frame-Options": "ALLOWALL",   # Override per-request in production
    "Content-Security-Policy": (
        "frame-ancestors 'self' http://localhost:3000 http://localhost:5173 http://frontend:80"
    ),
}

# ─── Guest token (for embedded dashboards) ────────────────────────────────────
GUEST_TOKEN_JWT_SECRET = os.environ.get("GUEST_TOKEN_JWT_SECRET", "guest_secret_change_me")
GUEST_TOKEN_JWT_ALGO = "HS256"
GUEST_TOKEN_JWT_EXP_SECONDS = 300  # 5 minutes; refresh before expiry

# ─── CORS ─────────────────────────────────────────────────────────────────────
ENABLE_CORS = True
CORS_OPTIONS = {
    "supports_credentials": True,
    "allow_headers": ["*"],
    "resources": {"*": {"origins": ALLOWED_ORIGINS}},
}

# ─── Proxy fix (behind nginx / Docker networking) ─────────────────────────────
ENABLE_PROXY_FIX = True
PROXY_FIX_CONFIG = {"x_for": 1, "x_proto": 1, "x_host": 1, "x_prefix": 1}

# ─── Public role for embedded guests ──────────────────────────────────────────
PUBLIC_ROLE_LIKE = "Gamma"

# ─── Misc ─────────────────────────────────────────────────────────────────────
ROW_LIMIT = 5000
SUPERSET_WEBSERVER_TIMEOUT = 300
SQLLAB_TIMEOUT = 300
SQLLAB_ASYNC_TIME_LIMIT_SEC = 600
