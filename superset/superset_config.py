import os
from urllib.parse import quote_plus
from flask_caching.backends.rediscache import RedisCache
from superset.security import SupersetSecurityManager


class WorkspaceSecurityManager(SupersetSecurityManager):
    # Superset 4 otherwise includes FAB security API permissions in Alpha/Gamma.
    # Keep user/role/permission administration exclusive to Superset Admins.
    ADMIN_ONLY_VIEW_MENUS = set(SupersetSecurityManager.ADMIN_ONLY_VIEW_MENUS) | {
        "User", "Role", "Permission", "ViewMenu", "PermissionViewMenu",
    }


CUSTOM_SECURITY_MANAGER = WorkspaceSecurityManager

# ─── Core ─────────────────────────────────────────────────────────────────────
SECRET_KEY = os.environ["SUPERSET_SECRET_KEY"]
SQLALCHEMY_DATABASE_URI = (
    f"postgresql+psycopg2://"
    f"{quote_plus(os.environ.get('DATABASE_USER', 'superset_user'))}:"
    f"{quote_plus(os.environ['DATABASE_PASSWORD'])}@"
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

# HTTP headers required for iframe embedding through the app proxy. Because the
# embed is served from the same origin as the app (`/embedded/...` on the app
# host), `'self'` works for localhost and real domains without extra host lists.
HTTP_HEADERS = {
    "X-Frame-Options": "ALLOWALL",
    "Content-Security-Policy": "frame-ancestors 'self'",
}

# ─── Guest token (for embedded dashboards) ────────────────────────────────────
GUEST_TOKEN_JWT_SECRET = os.environ["GUEST_TOKEN_JWT_SECRET"]
GUEST_TOKEN_JWT_ALGO = "HS256"
GUEST_TOKEN_JWT_EXP_SECONDS = 300  # 5 minutes; refresh before expiry

# ─── CORS ─────────────────────────────────────────────────────────────────────
ENABLE_CORS = True
CORS_OPTIONS = {
    "supports_credentials": False,
    "allow_headers": ["*"],
    "resources": {"*": {"origins": "*"}},
}

# ─── Proxy fix (behind nginx / Docker networking) ─────────────────────────────
ENABLE_PROXY_FIX = True
PROXY_FIX_CONFIG = {"x_for": 1, "x_proto": 1, "x_host": 1, "x_prefix": 1}

# Guest JWTs restrict a read-only role to the dashboards in their resource claims.
# Anonymous browsers must never inherit Gamma's editing permissions.
PUBLIC_ROLE_LIKE = None
GUEST_ROLE_NAME = "EmbeddedGuest"
SESSION_COOKIE_NAME = "superset_session"
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = "Lax"

# Backend-only personal authoring account provisioning through FAB's guarded API.
# Superset's Admin role controls these endpoints; personal authors use Alpha.
FAB_ADD_SECURITY_API = True


def configure_workspace(app):
    from flask import redirect, request
    from flask_login import current_user
    from urllib.parse import urlencode

    @app.before_request
    def require_authoring_session():
        # API requests authenticate through Superset itself (including guest JWTs).
        # Page requests need a real user before any create/owner operation runs.
        public = ("/login/", "/static/", "/api/", "/embedded/", "/health")
        if not request.path.startswith(public) and current_user.is_anonymous:
            return redirect("/login/?" + urlencode({"next": request.full_path.rstrip("?")}))
        return None


FLASK_APP_MUTATOR = configure_workspace

# ─── Misc ─────────────────────────────────────────────────────────────────────
ROW_LIMIT = 5000
SUPERSET_WEBSERVER_TIMEOUT = 300
SQLLAB_TIMEOUT = 300
SQLLAB_ASYNC_TIME_LIMIT_SEC = 600
