# teramera

**Money between friends, split fairly and settled simply.**

teramera is an expense-splitting app for friends — create groups, add expenses any way you like (equal, exact, percent, shares), pay from multiple people, include only who's involved, and settle up in the fewest payments possible.

- **Android app**: Kotlin + Jetpack Compose (Material 3), offline-first with Room
- **Backend**: Cloudflare Worker (TypeScript/Hono) + Cloudflare D1 database
- **Auth**: Google Sign-In (primary) or email + password with emailed verification codes

## Repository layout

```
app/       Android app (Kotlin, Compose, Hilt, Room, Retrofit)
worker/    Cloudflare Worker API (TypeScript, Hono, D1)
backend/   Legacy Spring Boot API — kept as reference, superseded by worker/
docs/      GitHub Pages site (landing page + privacy policy)
opendesign/ Design system + hi-fi mockups (opendesign plugin)
```

## Features

- Google Sign-In or email/password (with emailed 6-digit verification codes)
- Groups with invite links (deep link joins after sign-in; landing page offers the APK)
- Add members by phone number or email (email invites via SMTP — Gmail app password works, no domain needed)
- Expenses split **equal / exact / percent / shares**
- **Multiple payers** per expense, each with their own amount
- **Per-expense participants** — exclude anyone from a specific expense
- Debt simplification: settle up in the minimum number of payments
- Activity feed, multi-currency amounts (paise-accurate), offline-first with sync
- In-app updates: the app checks the server and prompts to download new builds

## Getting started

### Android app

```bash
# 1. Add your Google OAuth web client ID (from Google Cloud Console)
echo "google.webClientId=<WEB_CLIENT_ID>" >> local.properties

# 2. Build & install
./gradlew installDebug
```

The app talks to the deployed Worker at
`https://teramera-api.devbulchandani876.workers.dev` (set in
`app/src/main/java/com/example/teramera/core/network/NetworkModule.kt`).
For local backend dev, change `DEFAULT_BASE_URL` to `http://10.0.2.2:8080/`.

### Worker API

```bash
cd worker
npm install
npx wrangler login

# create the D1 database once, then paste its id into wrangler.jsonc
npx wrangler d1 create teramera
npx wrangler d1 execute teramera --remote --file schema.sql

# secrets
npx wrangler secret put JWT_SECRET          # random 32+ byte hex
npx wrangler secret put GOOGLE_CLIENT_ID    # OAuth web client ID
npx wrangler secret put SMTP_USER           # gmail address
npx wrangler secret put SMTP_PASS           # gmail app password

# deploy
npx wrangler deploy
```

API runs at `https://teramera-api.<your-subdomain>.workers.dev`.

### Tests

```bash
./gradlew :app:testDebugUnitTest     # Android: split engine, balances, simplifier, auth
cd backend && mvn test               # Spring Boot reference tests
```

## API overview

| Endpoint | Description |
|---|---|
| `POST /auth/google` | Sign in with a Google ID token |
| `POST /auth/email/register` · `/login` · `/otp` · `/verify` | Email + password auth with emailed codes |
| `GET /me` · `PATCH /me` | Profile |
| `GET/POST /groups` · `GET /groups/:id/detail` | Groups with balances & simplified debts |
| `POST /expenses` | Create expense — `payments[]` for multi-payer, `participantIds[]` for per-expense members |
| `POST /settlements` | Record a payment (either direction) |
| `GET /balances` | Friend-level nets for the home screen |
| `GET /users/find?phone= or ?email=` | Find a user to add |
| `POST /groups/:id/members` · `/join` · `/invite-email` | Membership & invites |
| `GET /app/version` · `GET /teramera.apk` | In-app update check & APK download |
| `GET /invite/:groupId` | Invite landing page (deep link + APK fallback) |

All expense amounts are integers in **paise** (1 ₹ = 100).

## Design

The teramera design system (warm cream surfaces, teal `#00848B` + violet `#825EA9`
"tere|mera" two-tone identity, Bricolage Grotesque + Instrument Sans) lives in
`opendesign/design-systems/teramera-product/` with hi-fi mockups under
`opendesign/mockups/`. Preview: `python3 -m http.server 8289` from the repo root,
then open `http://localhost:8289/opendesign/`.

## Status & notes

- v0.2.1 — core loop complete and multi-device synced
- OTP/email-code rate limit: 3 per 10 min per identifier
- `EXPOSE_DEV_OTP=true` returns codes in API responses — **must be `false` in production**
- SMS codes log to the Worker console (no SMS provider wired yet); email codes send via SMTP/Resend
- Offline-created expenses stay local until a future sync pass

## License

All rights reserved. Private project.
