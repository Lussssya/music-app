# Music App

Spring Boot backend for a music platform with catalog, listener activity, playlists, and recommendations.

## Local Setup

1. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

2. Run the app:

   ```bash
   mvn spring-boot:run
   ```

Flyway runs the schema and seed migrations automatically on startup.

## Frontend

The React shell lives in `frontend/`.

```bash
cd frontend
pnpm install
pnpm dev
```

Vite serves the UI on `http://localhost:5173` and proxies `/api` requests to the Spring Boot backend on `http://localhost:8080`.
