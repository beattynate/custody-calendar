# Custody Calendar Monorepo

## Structure
- `services/api`: Spring Boot API
- `apps/web`: Web app placeholder
- `apps/android`: Android app placeholder
- `db`: Shared database assets (if needed)

## Local development
1. Start Postgres:
   - `docker compose up -d postgres`
2. Run API from `services/api`:
   - `./mvnw spring-boot:run` (Git Bash)
   - `mvnw.cmd spring-boot:run` (PowerShell)
3. OpenAPI JSON:
   - `http://localhost:8080/v3/api-docs`

## Notes
- Flyway migrations live in `services/api/src/main/resources/db/migration`.
- JWT verification is configured as a resource server skeleton and expects a JWK set URI.
