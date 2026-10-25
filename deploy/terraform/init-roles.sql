\getenv app_password POSTGRES_PASSWORD
\getenv superset_password SUPERSET_DATABASE_PASSWORD
SELECT format('CREATE ROLE app_user LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') \gexec
SELECT format('CREATE ROLE superset_user LOGIN PASSWORD %L', :'superset_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'superset_user') \gexec
GRANT app_user, superset_user TO CURRENT_USER WITH INHERIT TRUE, SET TRUE;
SELECT 'CREATE DATABASE app_db OWNER app_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'app_db') \gexec
SELECT 'CREATE DATABASE superset OWNER superset_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'superset') \gexec
