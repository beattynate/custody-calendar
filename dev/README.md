# Dev Helpers (Local Only)

These files are for local/manual testing convenience only. Do not use them in production deployments.

## Stable Local Test Data

Run the seed SQL to create a predictable case + members + schedule rule:

PowerShell (recommended wrapper):

```powershell
.\dev\seed-local.ps1
```

PowerShell (direct SQL pipe, equivalent):

```powershell
Get-Content .\dev\seed-local.sql | docker exec -i custody-calendar-postgres psql -U custody -d custody_calendar
```

Fixed IDs created by the seed:

- Case ID: `11111111-1111-1111-1111-111111111111`
- Parent A ID: `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`
- Parent B ID: `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb`

## Stable Dev JWTs

Mint a long-lived token for Parent A (same subject every time):

```powershell
.\dev\jwt\mint-dev-jwt.ps1 -Subject "dev|parent-a" -Name "Parent A" -ExpiresInHours 8760
```

Mint Parent B token:

```powershell
.\dev\jwt\mint-dev-jwt.ps1 -Subject "dev|parent-b" -Name "Parent B" -ExpiresInHours 8760
```

## Important Notes

- API restarts should not invalidate tokens unless the dev JWT key changes (`dev/jwt/private-key.json`).
- Docker Postgres data persists across restarts via the `postgres_data` volume.
- Running integration tests against the same local DB will truncate tables and remove your seeded data.
