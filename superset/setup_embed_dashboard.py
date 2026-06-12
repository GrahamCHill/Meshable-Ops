"""
One-off setup script: creates a Postgres connection, dataset, chart and
dashboard in Superset, and enables embedding for that dashboard so the
frontend's "metric graphs" embed has something real to point at.

Uses session-cookie auth (not the JWT Bearer API) because Bearer-token
requests on this Superset image hit a current_user/AnonymousUserMixin bug
on any endpoint that needs the logged-in user object (chart create, /me, etc).

Run inside the superset_app container:
  docker exec superset_app python /app/setup_embed_dashboard.py
"""
import json
import os
import requests
import psycopg2
import uuid

BASE = os.environ.get("SUPERSET_INTERNAL_URL", "http://superset:8088")


def grant_public_datasource_access(database_name: str, table_name: str, dataset_id: int) -> None:
    """Grant the Public role (used by guest tokens) datasource_access on the
    given dataset. Without this, embedded charts fail with
    DATASOURCE_SECURITY_ACCESS_ERROR even though the dashboard itself loads."""
    view_menu_name = f"[{database_name}].[{table_name}](id:{dataset_id})"
    conn = psycopg2.connect(
        host=os.environ.get("DATABASE_HOST", "postgres"),
        port=os.environ.get("DATABASE_PORT", "5432"),
        dbname=os.environ.get("DATABASE_DB", "superset"),
        user=os.environ.get("DATABASE_USER", "superset_user"),
        password=os.environ.get("DATABASE_PASSWORD", "superset_pass"),
    )
    try:
        with conn, conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO ab_permission_view_role (id, permission_view_id, role_id)
                SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM ab_permission_view_role),
                       pv.id, r.id
                FROM ab_permission_view pv
                JOIN ab_permission p ON p.id = pv.permission_id AND p.name = 'datasource_access'
                JOIN ab_view_menu vm ON vm.id = pv.view_menu_id AND vm.name = %s
                CROSS JOIN ab_role r
                WHERE r.name = 'Public'
                  AND NOT EXISTS (
                      SELECT 1 FROM ab_permission_view_role pvr2
                      WHERE pvr2.permission_view_id = pv.id AND pvr2.role_id = r.id
                  )
                """,
                (view_menu_name,),
            )
            print("granted Public datasource_access on", view_menu_name, "rows:", cur.rowcount)
    finally:
        conn.close()


def sync_backend_dashboard_embed(embed_uuid: str, dashboard_id: int) -> None:
    """Upsert the shared app dashboard config with the real embed UUID."""
    conn = psycopg2.connect(
        host=os.environ.get("DATABASE_HOST", "postgres"),
        port=os.environ.get("DATABASE_PORT", "5432"),
        dbname=os.environ.get("APP_POSTGRES_DB", "app_db"),
        user=os.environ.get("APP_POSTGRES_USER", os.environ.get("DATABASE_USER", "superset_user")),
        password=os.environ.get("APP_POSTGRES_PASSWORD", os.environ.get("DATABASE_PASSWORD", "superset_pass")),
    )
    try:
        with conn, conn.cursor() as cur:
            cur.execute(
                """
                SELECT id
                FROM dashboard_embed_configs
                WHERE label = %s OR dashboard_id = %s
                ORDER BY created_at ASC
                LIMIT 1
                """,
                ("Metrics Overview", embed_uuid),
            )
            row = cur.fetchone()
            config_id = row[0] if row else str(uuid.uuid4())

            if row:
                cur.execute(
                    """
                    UPDATE dashboard_embed_configs
                    SET label = %s,
                        description = %s,
                        dashboard_id = %s,
                        guest_token_resource_id = %s,
                        sort_order = 0,
                        enabled = TRUE,
                        updated_at = NOW()
                    WHERE id = %s
                    """,
                    ("Metrics Overview", "Items created over time", embed_uuid, str(dashboard_id), config_id),
                )
            else:
                cur.execute(
                    """
                    INSERT INTO dashboard_embed_configs (
                        id,
                        label,
                        description,
                        dashboard_id,
                        guest_token_resource_id,
                        sort_order,
                        enabled
                    ) VALUES (%s, %s, %s, %s, %s, 0, TRUE)
                    """,
                    (config_id, "Metrics Overview", "Items created over time", embed_uuid, str(dashboard_id)),
                )

            cur.execute("DELETE FROM dashboard_embed_config_roles WHERE dashboard_embed_config_id = %s", (config_id,))
            cur.executemany(
                """
                INSERT INTO dashboard_embed_config_roles (dashboard_embed_config_id, role_name)
                VALUES (%s, %s)
                """,
                [(config_id, "ADMIN"), (config_id, "USER")],
            )
            print("synced_backend_dashboard_embed", config_id, embed_uuid, dashboard_id)
    finally:
        conn.close()


def login():
    s = requests.Session()
    r = s.post(f"{BASE}/login/", data={"username": "admin", "password": "admin"})
    r.raise_for_status()
    me = s.get(f"{BASE}/api/v1/me/")
    me.raise_for_status()
    assert me.json()["result"]["username"] == "admin", me.text
    return s


def main():
    s = login()

    # 1. Database connection -> app_db
    r = s.get(f"{BASE}/api/v1/database/?q=(filters:!((col:database_name,opr:eq,value:'App DB')))")
    r.raise_for_status()
    existing = r.json()["result"]
    if existing:
        database_id = existing[0]["id"]
    else:
        db_payload = {
            "database_name": "App DB",
            "sqlalchemy_uri": "postgresql+psycopg2://superset_user:superset_pass@postgres:5432/app_db",
            "expose_in_sqllab": True,
        }
        r = s.post(f"{BASE}/api/v1/database/", json=db_payload)
        r.raise_for_status()
        database_id = r.json()["id"]
    print("database_id", database_id)

    # 2. Dataset on items table
    r = s.get(f"{BASE}/api/v1/dataset/?q=(filters:!((col:table_name,opr:eq,value:items)))")
    r.raise_for_status()
    existing = [d for d in r.json()["result"] if d["database"]["id"] == database_id]
    if existing:
        dataset_id = existing[0]["id"]
    else:
        ds_payload = {"database": database_id, "table_name": "items", "schema": "public"}
        r = s.post(f"{BASE}/api/v1/dataset/", json=ds_payload)
        r.raise_for_status()
        dataset_id = r.json()["id"]
    print("dataset_id", dataset_id)

    # Guest tokens authenticate as the "Public" role; without explicit
    # datasource access, embedded charts on this dataset fail to load data.
    grant_public_datasource_access("App DB", "items", dataset_id)

    # 3. Chart: count of items created over time (timeseries bar)
    r = s.get(f"{BASE}/api/v1/chart/?q=(filters:!((col:slice_name,opr:eq,value:'Items Created Over Time')))")
    r.raise_for_status()
    existing = r.json()["result"]
    if existing:
        chart_id = existing[0]["id"]
    else:
        chart_params = {
            "datasource": f"{dataset_id}__table",
            "viz_type": "echarts_timeseries_bar",
            "x_axis": "created_at",
            "time_grain_sqla": "P1D",
            "metrics": [{
                "expressionType": "SQL",
                "sqlExpression": "COUNT(*)",
                "label": "Items Created",
            }],
            "groupby": [],
            "adhoc_filters": [],
            "row_limit": 10000,
        }
        chart_payload = {
            "slice_name": "Items Created Over Time",
            "viz_type": "echarts_timeseries_bar",
            "datasource_id": dataset_id,
            "datasource_type": "table",
            "params": json.dumps(chart_params),
        }
        r = s.post(f"{BASE}/api/v1/chart/", json=chart_payload)
        r.raise_for_status()
        chart_id = r.json()["id"]
    print("chart_id", chart_id)

    # 4. Dashboard containing the chart
    r = s.get(f"{BASE}/api/v1/dashboard/?q=(filters:!((col:dashboard_title,opr:eq,value:'Metrics Overview')))")
    r.raise_for_status()
    existing = r.json()["result"]
    if existing:
        dashboard_id = existing[0]["id"]
    else:
        dash_payload = {
            "dashboard_title": "Metrics Overview",
            "slug": "metrics-overview",
            "published": True,
        }
        r = s.post(f"{BASE}/api/v1/dashboard/", json=dash_payload)
        r.raise_for_status()
        dashboard_id = r.json()["id"]
    print("dashboard_id", dashboard_id)

    position_json = {
        "DASHBOARD_VERSION_KEY": "v2",
        "ROOT_ID": {"type": "ROOT", "id": "ROOT_ID", "children": ["GRID_ID"]},
        "GRID_ID": {
            "type": "GRID",
            "id": "GRID_ID",
            "children": ["ROW-1"],
            "parents": ["ROOT_ID"],
        },
        "ROW-1": {
            "type": "ROW",
            "id": "ROW-1",
            "children": ["CHART-1"],
            "parents": ["ROOT_ID", "GRID_ID"],
            "meta": {"background": "BACKGROUND_TRANSPARENT"},
        },
        "CHART-1": {
            "type": "CHART",
            "id": "CHART-1",
            "children": [],
            "parents": ["ROOT_ID", "GRID_ID", "ROW-1"],
            "meta": {
                "chartId": chart_id,
                "width": 12,
                "height": 50,
            },
        },
    }

    update_payload = {
        "position_json": json.dumps(position_json),
        "json_metadata": json.dumps({
            "expanded_slices": {},
            "refresh_frequency": 0,
            "default_filters": "{}",
        }),
    }
    r = s.put(f"{BASE}/api/v1/dashboard/{dashboard_id}", json=update_payload)
    r.raise_for_status()

    # Make sure the chart is associated with the dashboard
    r = s.put(f"{BASE}/api/v1/chart/{chart_id}", json={"dashboards": [dashboard_id]})
    r.raise_for_status()

    # 5. Enable embedding for the dashboard
    embed_payload = {
        "allowed_domains": [
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
        ]
    }
    r = s.post(f"{BASE}/api/v1/dashboard/{dashboard_id}/embedded", json=embed_payload)
    if r.ok:
        embed_uuid = r.json()["result"]["uuid"]
    else:
        existing_embed = s.get(f"{BASE}/api/v1/dashboard/{dashboard_id}/embedded")
        existing_embed.raise_for_status()
        embed_uuid = existing_embed.json()["result"]["uuid"]
    print("embed_uuid", embed_uuid)
    sync_backend_dashboard_embed(embed_uuid, dashboard_id)

    print(json.dumps({
        "database_id": database_id,
        "dataset_id": dataset_id,
        "chart_id": chart_id,
        "dashboard_id": dashboard_id,
        "embed_uuid": embed_uuid,
    }))


if __name__ == "__main__":
    main()
