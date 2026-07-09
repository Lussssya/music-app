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
