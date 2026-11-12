# Room Booking

A mock room-booking app that is the test bed for a software factory. Start with [`docs/factory.md`](docs/factory.md); vocabulary lives in [`GLOSSARY.md`](GLOSSARY.md) and decisions in [`docs/adr/`](docs/adr/).

## Layout

| Path | What |
|---|---|
| `openapi.yaml` | The Contract: source of truth for the API ([ADR-0002](docs/adr/0002-contract-first-with-openapi.md)) |
| `backend/` | Java 21, Spring Boot 3.5, Maven, PostgreSQL + Flyway + jOOQ. Package by feature: `<feature>/domain` (plain Java), `web`, `persistence`, enforced by ArchUnit |
| `frontend/` | React 18, Vite, TypeScript, React Router 6, TanStack Query, Tailwind 3 + shadcn/ui |
| `compose.yaml` | PostgreSQL for local development |

## Prerequisites

Java 21, Node.js and pnpm at the versions in `.tool-versions` and `package.json` (`packageManager`), and Docker.

```sh
pnpm install          # also installs the pre-commit hook
```

## Run locally

```sh
cd backend && ./mvnw spring-boot:run   # starts PostgreSQL from compose.yaml, serves http://localhost:8080/api
pnpm -C frontend dev                   # http://localhost:5173, proxies /api to the backend
```

## Checks

```sh
cd backend && ./mvnw verify            # format, Error Prone, unit + integration tests, ArchUnit
pnpm -C frontend lint                  # Biome
pnpm -C frontend typecheck
pnpm -C frontend test                  # Vitest
cd backend && ./mvnw -DskipTests package && pnpm -C frontend e2e   # Playwright against the production build
git ls-files -z -- scripts ':(exclude)*.jq' | xargs -0 shellcheck   # these run in CI's identity job
scripts/tests/check-commit-authors.test.sh
scripts/tests/sync-main.test.sh
scripts/tests/factory-metrics.test.sh
scripts/tests/staged-changes-affect-frontend.test.sh
```

The Spring interfaces and the TypeScript client are generated from `openapi.yaml`, and the jOOQ classes from the Flyway migrations applied to a throwaway PostgreSQL container ([ADR-0004](docs/adr/0004-data-access-with-jooq.md)), on every build; never edit or commit them. The backend build therefore needs Docker.
