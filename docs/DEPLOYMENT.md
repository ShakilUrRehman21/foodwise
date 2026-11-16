# Deploying FoodWise

FoodWise ships with a [Render](https://render.com) Blueprint (`render.yaml`) that creates three things in the
Singapore region (closest Render region to India):

| Resource | What it runs |
|---|---|
| `foodwise-api` | Spring Boot API from `backend/Dockerfile` (Tesseract OCR included), `prod` profile |
| `foodwise-web` | Next.js app from `frontend/Dockerfile`; proxies `/api/*` to the API |

The `prod` profile requires Clerk sign-in (the local dev sign-in is refused), so set up Clerk first.

## 0. Database (Neon)

Render allows one free database per account, so FoodWise uses [Neon](https://neon.tech) (free, no expiry).
Create a project, then from **Connection details** take the host, database, user and password:

- `DATABASE_URL` = `jdbc:postgresql://<host>/<database>?sslmode=require`
- `DATABASE_USERNAME` = `<user>`
- `DATABASE_PASSWORD` = `<password>`

## 1. Clerk

1. Create an application at <https://dashboard.clerk.com>.
2. Copy the **publishable key** (`pk_...`) and **secret key** (`sk_...`).
3. Note your Frontend API URL, e.g. `https://your-app.clerk.accounts.dev`. From it:
   - `CLERK_ISSUER` = `https://your-app.clerk.accounts.dev`
   - `CLERK_JWKS_URI` = `https://your-app.clerk.accounts.dev/.well-known/jwks.json`
4. Under **Sessions → Customize session token**, add the claim `{"email": "{{user.primary_email_address}}"}`.

## 2. Render

1. Render dashboard → **New → Blueprint** → select the `foodwise` GitHub repo.
2. Fill in the values Render asks for:

   | Service | Variable | Value |
   |---|---|---|
   | api | `FRONTEND_URL`, `CLERK_AUTHORIZED_PARTIES` | `https://foodwise-web.onrender.com` |
   | api | `CLERK_ISSUER`, `CLERK_JWKS_URI` | from step 1 |
   | api | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | from step 0 |
   | api | `CLERK_SECRET_KEY` | `sk_...` |
   | api | `CLERK_WEBHOOK_SECRET` | from step 3 below (put any placeholder first) |
   | web | `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | `pk_...` |
   | web | `CLERK_SECRET_KEY` | `sk_...` |

3. Deploy. Flyway creates and seeds the database on the API's first start.
4. If Render gave a service a different URL (the name was taken), update `API_PROXY_TARGET` on the web service
   and `FRONTEND_URL` / `CLERK_AUTHORIZED_PARTIES` on the API, then redeploy the web service
   (`API_PROXY_TARGET` is read at build time).

## 3. Clerk webhook (optional)

Users are created on their first request anyway; the webhook keeps names and emails in sync.
Clerk dashboard → **Webhooks** → endpoint `https://foodwise-api.onrender.com/api/webhooks/clerk`, events
`user.created` and `user.updated`. Copy the signing secret into `CLERK_WEBHOOK_SECRET` on the API.

## Things to know about the free plan

- Free web services sleep after 15 minutes without traffic; the first request then takes about a minute.
- Neon's free database pauses when idle; the first query after a pause takes a second or two.
- Free services have no persistent disk, so uploaded receipt images are lost on restart. Scans are reviewed and
  confirmed within minutes, so this only affects unfinished scans.
- 512 MB of memory is tight for Spring Boot plus OCR. If the API restarts under load, move it to the Starter plan.

## Smoke test after deploying

- `https://foodwise-api.onrender.com/api/v1/health` returns `{"status":"UP"}`.
- Open `https://foodwise-web.onrender.com`, sign up, finish onboarding and add an item.
