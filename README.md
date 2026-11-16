# FoodWise

[![CI](https://github.com/ShakilUrRehman21/foodwise/actions/workflows/ci.yml/badge.svg)](https://github.com/ShakilUrRehman21/foodwise/actions/workflows/ci.yml)

**Know what you have. Waste less.**

FoodWise is a kitchen companion for Indian households. It tracks groceries and leftovers, shows what is about to expire, suggests recipes that use up that food first, keeps a shared shopping list, and shows how many rupees were spent and wasted.

The specifications it follows are in [`docs/`](docs/).

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 15 (App Router), React 19, TypeScript, Tailwind CSS 4, TanStack Query, Clerk (optional) |
| Backend | Spring Boot 3.5, Java 21, Spring Security (JWT resource server), JPA/Hibernate, Flyway |
| Database | PostgreSQL 16 |
| Scanning | Local Tesseract 5 OCR (Tess4J, with a CLI fallback); optional local ONNX Runtime (YOLOv8). No cloud or generative AI. |
| Delivery | Docker Compose (`db`, `api`, `web`) |

## Features

- **Inventory**
  - Fridge, freezer and pantry batches, with autosuggest from a catalog of 206 Indian grocery items that includes Hindi names.
  - Partial use, moving and editing batches.
  - Discarding records a reason and the ₹ value lost.
  - CSV export.
  - Every quantity change runs in a row-locked transaction and is written to an append-only event ledger.
- **Expiry**
  - Each item is classified as Fresh, Use soon (1–3 days), Today or Expired.
  - A triage screen lists items that need attention.
  - A 15-minute scheduler sends each user one morning digest per kitchen, at the time they choose. The database stops it sending duplicates.
- **Receipt scan**
  - The pipeline:
    1. A receipt photo is cleaned up (grayscale, deskew, denoise, threshold).
    2. Tesseract reads the text.
    3. A parser built for Indian receipts (Blinkit, Zepto, D-Mart, kirana) turns it into line items.
    4. Each line is fuzzy-matched to the catalog.
    5. A **mandatory review screen** lets the user correct the lines.
    6. One transaction creates the batches and the purchase record.
  - Lines where quantity × rate doesn't match the printed amount are flagged for review.
  - Each kitchen can run 20 scans per hour.
- **Recipes**
  - 83 seeded Indian recipes.
  - A readiness score that ranks recipes higher when they use food that expires soon.
  - Diet filters, including Jain.
  - Servings scaling and step timers.
  - "I cooked this" deducts ingredients, taking the soonest-expiring stock first.
  - Missing ingredients can be added to the shopping list.
  - Favourites and a weekly meal plan.
- **Leftovers**: log cooked dishes with an "eat by" date and get ideas for reusing them, such as dal paratha or lemon rice.
- **Shopping**
  - A shared list that doesn't create duplicates.
  - Items that are running low are suggested automatically.
  - An in-store mode.
  - Checked-off items can be imported into inventory.
- **Analytics**
  - Monthly spend.
  - Waste by category, by reason and top items.
  - The value of food used just before it expired.
  - A 6-month trend.
- **Kitchens**
  - Each user can have several kitchens.
  - Owners invite flatmates by email.
  - Each kitchen's data is kept separate; non-members get a 403.

## Quick start (Docker)

You need Docker Desktop.

```bash
docker compose up --build -d
```

| What | URL |
|---|---|
| App | http://localhost:3000 |
| API | http://localhost:8080 (also proxied at http://localhost:3000/api) |
| Swagger UI | http://localhost:8080/api/docs |
| Health | http://localhost:8080/api/v1/health |

Open the app, click **Get started**, and sign in with any email. Without Clerk keys, the app uses a local dev sign-in.

To stop the app:

```bash
docker compose down     # data stays in the named Postgres volume
docker compose down -v  # also deletes the database
```

### Windows helper scripts

The `scripts/` folder has scripts you can double-click instead of typing commands. Each one writes a log to `scripts/logs/`.

| Script | What it does |
|---|---|
| `1-check-env.bat` | Checks for Java, Node, Docker and Git |
| `2-backend-build-test.bat` | Downloads a portable Maven into `scripts/.tools`, starts Docker Desktop, and runs the backend tests |
| `3-start-app.bat` | Runs `docker compose up --build -d`, waits for the health check, and opens the app |
| `4-verify-and-restart.bat` | Runs the tests, then rebuilds and restarts the app |
| `5-stop-app.bat` | Stops the containers and Docker Desktop |
| `run-e2e.bat` | Installs Playwright, starts the app and runs the end-to-end tests |

## Local development

Prerequisites:

- Java 21
- Maven 3.9+
- Node 20+
- PostgreSQL 16 (or `docker compose up -d db`)
- Tesseract 5, for receipt scanning. On Windows, install it from <https://github.com/UB-Mannheim/tesseract/wiki> and set `TESSDATA_PATH=C:\Program Files\Tesseract-OCR\tessdata`.

```bash
# 1. Database
docker compose up -d db

# 2. Backend. The dev profile uses local sign-in and enables Swagger.
#    Flyway creates and seeds the schema on first start.
cd backend
mvn spring-boot:run

# 3. Frontend
cd ../frontend
npm install
npm run dev   # http://localhost:3000; /api/* is proxied to API_PROXY_TARGET (default http://localhost:8080)
```

### Configuration

Every setting is listed in `backend/.env.example` and `frontend/.env.example`. The main ones:

| Variable | Where | Purpose |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | backend | PostgreSQL connection (default `localhost:5432/foodwise`, `foodwise`/`foodwise`) |
| `AUTH_MODE` | backend | `DEV` (local tokens) or `CLERK` |
| `TESSDATA_PATH` | backend | Folder that contains `eng.traineddata` |
| `HAUL_MODEL_PATH` | backend | Optional ONNX model for countertop grocery recognition |
| `API_PROXY_TARGET` | frontend | Where Next.js forwards `/api/*` |
| `NEXT_PUBLIC_API_URL` | frontend | Leave empty to use the same-origin proxy, or set it to call the API directly |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` / `CLERK_SECRET_KEY` | frontend | Turn on Clerk sign-in |

## Tests

```bash
cd backend && mvn test
```

This runs the unit tests and the Postgres integration tests. The integration tests use Testcontainers and are skipped when Docker isn't available.

To run the integration tests against an existing database instead:

```bash
FOODWISE_TEST_DB_URL=jdbc:postgresql://localhost:5432/foodwise_test FOODWISE_TEST_DB_USER=postgres mvn test
```

To check the frontend:

```bash
cd frontend && npm run typecheck && npm run lint && npm run build
```

End-to-end tests (Playwright) run against a running stack (`docker compose up -d`, or `E2E_BASE_URL` for another URL):

```bash
cd frontend
npx playwright install chromium   # first time only
npm run test:e2e
```

They cover sign-up → onboarding → adding and discarding food, cooking a recipe (and refusing when stock is short),
shopping list → inventory, and kitchen isolation. On Windows, `scripts/run-e2e.bat` does all of it.

### Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request: backend tests, frontend typecheck/lint/build, then
the end-to-end suite against the full Docker Compose stack.

## Deployment

`render.yaml` deploys Postgres, the API and the web app to Render in one step. See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Authentication

- **Local development** is the default when no Clerk key is set.
  - The backend issues short-lived HS256 tokens from `POST /api/v1/dev/token`.
  - The backend refuses to start in this mode under the `prod` profile.
- **Clerk** is for production.
  - Frontend: set `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` and `CLERK_SECRET_KEY`.
  - Backend: set `AUTH_MODE=CLERK`, `CLERK_JWKS_URI`, `CLERK_ISSUER`, `CLERK_AUTHORIZED_PARTIES` and `CLERK_WEBHOOK_SECRET`.
  - Point a Clerk webhook (`user.created`, `user.updated`) at `POST /api/webhooks/clerk`.
  - Users are also created on their first request if they don't exist yet.

## Design decisions

- **No invented expiry dates.** Following PRD FR-EXP-001, the catalog's shelf-life columns are seeded as `NULL`, and users always enter expiry dates. Validated guidance can be added later in a migration; the UI already shows an "Estimated" badge for estimated dates.
- **Receipt OCR is always reviewed.** Nothing from a scan reaches inventory until the user confirms the lines. Lines with low confidence or inconsistent numbers are highlighted.
- **Countertop haul recognition is built but turned off** until an approved ONNX model is configured with `HAUL_MODEL_PATH`. The dataset licence decision (UD-004) is still open.
- **Schema changes are additive only.** Once a migration has run anywhere, add a new `V<n>__` file rather than editing an existing one. Seed migrations are generated from `backend/tools/seed/*.py`.
- **Scan images are stored on local disk** (UD-003 MVP option). Digests are in-app only for now (UD-005).

## Project layout

```
backend/    Spring Boot modular monolith
            common, config, security, user, kitchen, catalog, inventory, expiry, notification,
            scan, recipe, leftover, mealplan, shopping, analytics, dashboard
            + Flyway migrations (src/main/resources/db/migration) + tests
frontend/   Next.js app: (app) route group for the signed-in shell, services/ API client,
            hooks/, components/layout, components/features
docs/       PRD, system design, database schema, UI specification, roadmap
scripts/    Windows helper scripts (build, test, start, stop)
```
