# Deployment and database safety

## Local Docker stack

From the backend directory, copy `.env.example` to `.env`, replace every
placeholder with a unique value, and run:

```powershell
docker compose up --build -d
```

The application does not create or alter database objects at startup. The
PostgreSQL database must already contain a schema compatible with the
application; startup validates it and fails if required tables or columns are
missing. A new empty Docker volume is not initialized automatically.

## Production

Set `SPRING_PROFILES_ACTIVE=prod`. Use managed PostgreSQL and Redis with TLS,
and set `SUPABASE_DB_URL`, `SUPABASE_USERNAME`, `SUPABASE_PASSWORD`,
`REDIS_URL`, `APP_CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, and the bootstrap
administrator variables through the platform's secret manager. Set
`GROQ_API_KEY` only when AI forecasting is enabled. The frontend's
`VITE_API_BASE_URL` is embedded at build time and must point to the public
HTTPS API URL.

The default, production, and Docker profiles keep Hibernate at
`ddl-auto: validate`. Database
credentials and connection settings are supplied through the existing
environment variables; do not put production credentials in source control or
command history.

## Database behavior

The backend has no migration runner or schema initializer. Hibernate validates
the mapped schema without changing database objects or records. Spring Batch
schema initialization is also disabled.

This project change does not connect to or alter Supabase. If the existing
database has a `flyway_schema_history` table, leave it untouched; its presence
does not cause the backend to run migrations.

The repository's regular tests use an isolated in-memory H2 database and may
create/drop test tables there. Those test-only settings do not apply to
Supabase or other application databases.

## Application data

The application does not seed supplier-product associations. Existing
purchase orders retain their supplier, product, and order-time
`purchase_unit_price`; historical orders are not converted into catalogue
mappings. Existing stock rows and user warehouse assignments are not modified.

Forecasting uses recorded `OUTBOUND` movements only. It stores daily history
and predictions per existing stock allocation, and does not change stock
quantities or create purchase orders.

Spring Batch's database schema initialization is disabled in all profiles.
Provision any required Batch metadata tables separately before enabling Batch
jobs.

Back up the database and verify restoration before rollout. Restrict CORS to
the exact HTTPS frontend origins, terminate TLS at the hosting platform or
reverse proxy, and do not publish `.env` files.
