# Custody Calendar Web MVP

## Run locally

```bash
npm install
npm run dev
```

Open `http://localhost:5173`.

## Required API endpoints
- `POST /api/v1/cases/{caseId}/schedule/solve`
- `POST /api/v1/cases/{caseId}/schedule/solve/accept`
- `GET /api/v1/cases/{caseId}/schedule?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/v1/cases/{caseId}/events?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `POST /api/v1/cases/{caseId}/events`
- `GET /api/v1/cases/{caseId}/people`
