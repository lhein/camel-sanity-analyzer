# Camel Sanity Analyzer

Analysis tool for Apache Camel components: lists all (including transitive) Maven dependencies of a component at a given version and enriches them with health data so you can spot stale/dead dependencies at a glance.

## Features

- Full transitive Maven dependency tree resolution (via Maven Resolver / Aether)
- Each dependency is enriched with:
  - Maven Central data (latest version, release date)
  - GitHub data (last commit, contributors, stars, archived flag)
  - deps.dev (license, dependents, source repo)
  - OSV.dev (CVEs)
  - OpenSSF Scorecard (objective health score)
- Aggregated health score per dependency with status `HEALTHY` / `OUTDATED` / `WARNING` / `CRITICAL`
- Web UI with dashboard, sortable/filterable table, dependency tree graph, and detail drawer
- In-memory cache (Caffeine) for API calls

## Requirements

- JDK 21
- Maven 3.9+
- Node.js 20+

## Setup

### 1. GitHub token (recommended)

Without a token, GitHub limits unauthenticated calls to 60 per hour, which is not enough to analyze larger components.

1. Create a Personal Access Token: https://github.com/settings/tokens (no scope is needed for public repos)
2. Copy `backend/config.example.yml` to `backend/config.yml`
3. Paste the token into `config.yml`

### 2. Start the backend

```bash
cd backend
mvn spring-boot:run
```

The backend listens on `http://localhost:8080`.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend is served at `http://localhost:5173`.

## Project structure

```
camel-sanity-analyzer/
├── backend/      Spring Boot REST API
└── frontend/     React + Vite + TypeScript
```
