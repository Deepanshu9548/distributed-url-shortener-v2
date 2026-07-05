# FRONTEND EXECUTION BRIEF — Distributed URL Shortener v2

You are an autonomous frontend-engineer agent. Build a web UI for the
existing Java/Spring Boot URL-shortener backend. The backend is complete
and running — you consume its REST API, you do not modify it.

Deliver a small, interview-quality SPA that showcases the backend's
capabilities cleanly. No feature bloat. Ship an MVP that WORKS.

---

## 0. Context

**Backend repo**: https://github.com/Deepanshu9548/distributed-url-shortener-v2
Clone it and read `README.md`, `docs/DEFENSE_NOTES.md`, and the endpoints
below. The backend is Spring Boot 3.3 on Java 17 bytecode, uses JWT auth,
sharded PostgreSQL, Redis, Kafka.

You are NOT building a MERN stack (the backend is already Java + Postgres —
no Node/Mongo needed). Build a **React SPA** that talks to the existing
Spring Boot REST API. The name "MERN" in the original ask is shorthand for
"React frontend" — take it as that.

**Where the code lives**: NEW folder `frontend/` at the root of the SAME
repo. Do not touch anything outside `frontend/`.

**Base URL**: `http://localhost:8080` in dev (or whatever the backend
serves at). Configure via `VITE_API_BASE_URL` env, default to
`http://localhost:8080`.

---

## 1. Tech stack (fixed — do NOT bikeshed)

- **Vite + React 18 + TypeScript** (fastest dev loop, tiny footprint).
  - Do NOT use Next.js unless you have a real SSR requirement. We don't.
- **TanStack Query v5** for server state (caching, refetch, mutations).
- **Zustand** for local UI state (auth tokens in memory + refresh token in
  httpOnly-ish localStorage with rotation on refresh).
- **React Router v6** for routing.
- **Tailwind CSS v3** + **shadcn/ui** components (copy-in components; no
  monolithic UI library).
- **react-hook-form** + **zod** for forms and validation.
- **axios** for HTTP (interceptors handle 401 → refresh, 429 → Retry-After
  toast).
- **sonner** or **react-hot-toast** for toasts.
- **recharts** for the one stats chart (kept minimal).
- Node 20+, `pnpm` preferred (`npm` OK if pnpm isn't available).

Do NOT add: Redux, MobX, Chakra, MUI, styled-components, moment (use
`Intl.DateTimeFormat`), lodash (native ES).

---

## 2. Pages / routes (this is the whole app)

| Route | Auth | Purpose |
|---|---|---|
| `/` | public | Landing: pitch + a public shorten form (unauthenticated writes are gated by backend — see §5). Show the last 3 links the user created in this browser session (localStorage cache, code + short URL only). |
| `/login` | public | Email + password. Store tokens. Redirect to `/dashboard`. |
| `/register` | public | Email + password + confirm. Auto-login on success. |
| `/dashboard` | protected | Paged table of the user's links (`GET /api/me/links`). Columns: short code, long URL (truncated + tooltip), created, click count (from stats endpoint, batch-fetch as we render), row actions (copy short URL, edit, delete). |
| `/links/new` | protected | Fuller create form: long URL, optional custom alias, optional TTL / expiresAt. |
| `/links/:code` | protected + owner | Detail view: metadata + stats chart (clickCount, lastClickAt), edit form, delete. |
| `*` | — | 404. |

Header: brand, `/dashboard`, `/links/new`, user email dropdown (logout).
Footer: link to backend GitHub, version tag from `import.meta.env.VITE_APP_VERSION` (bake at build).

---

## 3. Backend API — canonical reference

**All error responses are `{"error": "..."}` with an HTTP status code.**
Rate-limit 429 responses include `Retry-After: <seconds>`.

### Auth (public POSTs)
- `POST /api/auth/register` — `{email, password}` → 201 `{userId, email}` | 409
- `POST /api/auth/login` — `{email, password}` → 200 `{accessToken, refreshToken, tokenType:"Bearer", expiresIn}` | 401
- `POST /api/auth/refresh` — `{refreshToken}` → 200 same shape as login | 401
- `POST /api/auth/logout` — `{refreshToken}` + `Authorization: Bearer <access>` → 204

Password policy (client-side must MATCH server): 8–128 chars, ≥1 letter, ≥1 digit.
Email: valid RFC address, max 320 chars.

### Links
- `POST /api/links` — bearer, header `Idempotency-Key: <uuid>`,
  `{longUrl, customAlias?, ttlSeconds?, expiresAt?}` →
  201 `{shortCode, shortUrl, longUrl, expiresAt}` | 200 on idempotent replay | 400 | 409 (alias)
- `GET /api/links/{code}` — public → 200 metadata | 404
- `PUT /api/links/{code}` — bearer, owner-only, `{longUrl?, expiresAt?, ttlSeconds?}` → 200 | 404
- `DELETE /api/links/{code}` — bearer, owner-only → 204 | 404
- `GET /api/me/links?page=0&size=20` — bearer → `{items:[{shortCode, createdAt}], page, size, totalElements, totalPages}`
- `GET /api/links/{code}/stats` — bearer, owner-only → `{shortCode, clickCount, lastClickAt}` | 404

### Redirect
- `GET /{code}` — public → 302 Location: <longUrl> | 404 | 503

Short-code regex: `[0-9a-zA-Z_-]{1,32}`.

### Rate-limit response (any endpoint)
`429` + `Retry-After: <seconds>` header + JSON body. Surface as a toast +
disable the offending action for that many seconds.

### Content types
All request/response bodies are `application/json` except the 302 redirect.

---

## 4. Auth handling (do this exactly)

- **Access token** (short-lived): keep in memory only (Zustand store).
- **Refresh token** (7d): store in `localStorage['refreshToken']`. Yes,
  localStorage is XSS-vulnerable — sanitize all user-rendered strings and
  never `dangerouslySetInnerHTML`. A proper prod app would use httpOnly
  cookies; that requires backend changes we're not doing here. Note this
  tradeoff in the frontend README.
- Axios request interceptor: attach `Authorization: Bearer <access>` if
  present.
- Axios response interceptor: on 401, try ONE refresh with the refresh
  token, retry the original request; on refresh failure, wipe tokens and
  redirect to `/login`.
- Idempotency-Key: generate a fresh `crypto.randomUUID()` per submit of the
  create form; if the user resubmits the same form without navigating,
  reuse the same key (natural retry protection).

---

## 5. Guardrails matching backend behavior

- Landing-page shortener form: since `POST /api/links` requires auth,
  either (a) redirect anonymous submitters to `/register` after previewing
  the URL, or (b) show a "Sign up to shorten" CTA. Do NOT lie about
  unauthenticated shortening working.
- Alias blocklist (client-side pre-check, save a round-trip):
  `{api, auth, actuator, swagger-ui, metrics, health, admin}`. Server
  will still enforce.
- Long URL client-side validation: `http(s)://`, max 8192 chars, reject
  javascript:/data: schemes, reject alias inputs outside
  `[0-9a-zA-Z_-]{4,32}`.
- Copy-to-clipboard uses `navigator.clipboard.writeText` with a fallback
  toast if the API is blocked.

---

## 6. Design system

- Tailwind config: brand color = neutral slate + one accent (indigo-500).
  Dark mode toggle via `class`-strategy, prefers-color-scheme default.
- Typography: system font stack, no web fonts.
- Layout: max-width 6xl, generous whitespace.
- shadcn/ui components used: Button, Input, Label, Card, Table, Dialog,
  Toast, DropdownMenu, Tooltip, Skeleton, Badge, Tabs.
- No hero images/illustrations — clean typographic landing page.
- Focus states visible, keyboard-nav for the table (arrow keys optional
  but Tab/Enter must work).
- Accessibility: label every input, aria-live for toasts, correct heading
  hierarchy, prefers-reduced-motion respected.

---

## 7. Folder structure

```
frontend/
├── package.json
├── pnpm-lock.yaml
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.ts
├── postcss.config.js
├── index.html
├── .env.example                # VITE_API_BASE_URL=http://localhost:8080
├── README.md                   # quick-start, arch overview, tradeoffs
├── public/
└── src/
    ├── main.tsx
    ├── App.tsx                 # routes
    ├── lib/
    │   ├── api.ts              # axios instance + interceptors
    │   ├── auth-store.ts       # zustand: accessToken, user, refresh flow
    │   ├── query-client.ts     # TanStack Query defaults
    │   └── validators.ts       # zod schemas
    ├── hooks/
    │   ├── use-links.ts        # useMyLinks(), useLink(code), useCreateLink()...
    │   ├── use-auth.ts
    │   └── use-stats.ts
    ├── components/
    │   ├── ui/                 # shadcn generated
    │   ├── link-table.tsx
    │   ├── link-form.tsx
    │   ├── stats-chart.tsx
    │   ├── nav-bar.tsx
    │   └── protected-route.tsx
    ├── pages/
    │   ├── landing.tsx
    │   ├── login.tsx
    │   ├── register.tsx
    │   ├── dashboard.tsx
    │   ├── link-new.tsx
    │   ├── link-detail.tsx
    │   └── not-found.tsx
    └── styles/
        └── globals.css         # Tailwind directives
```

---

## 8. Testing (minimal, real)

- **Vitest** + **@testing-library/react** for component tests.
- **MSW** (Mock Service Worker) to mock the backend in tests.
- Coverage floor is behavior, not %: test at least
  1. Login → dashboard happy path (with MSW returning tokens + links list).
  2. Rate-limited create surfaces the 429 toast + disables submit.
  3. 401 during a query triggers refresh; on refresh failure, redirect to /login.
  4. Password validator matches backend policy.
  5. Owner-only URL: 404 → redirects to /dashboard with an "unknown link" toast.
- Do NOT ship snapshot tests. Assert what the user sees.
- No E2E (Cypress/Playwright) in this MVP — call it out as a follow-up.

---

## 9. DX / build

- `pnpm dev` — Vite dev server on 5173, proxies `/api/*` and `/{code}` to
  backend on 8080 (configure in `vite.config.ts` so cookies/CORS aren't
  a concern locally).
- `pnpm build` — production bundle to `dist/`.
- `pnpm test` — Vitest run.
- `pnpm lint` — ESLint (typescript-eslint) + Prettier check.
- No CI setup for the frontend in THIS pass — call it out as follow-up
  (a Github Actions workflow to lint+test+build on PR).

---

## 10. Deployment plan (document, don't build)

In `frontend/README.md`, describe how you'd ship this:
1. `pnpm build` → static `dist/`.
2. Serve behind nginx (compose already has an nginx container — extend its
   config to serve `/` from `dist/` and proxy `/api/*` + short-code paths
   to the app). Do NOT change compose yet — describe the change.
3. Env at build time: `VITE_API_BASE_URL` empty in prod (same origin via
   nginx).

---

## 11. Hard rules

- Do NOT modify anything outside `frontend/` (no backend edits, no ADRs,
  no `PROGRESS.md`).
- Do NOT push to `main`. Work on a branch `frontend-mvp`.
- Do NOT invent endpoints. Use only §3.
- No hardcoded secrets/tokens anywhere.
- No emojis in code or copy unless truly necessary for the UI text.
- Every user-facing error message maps 1:1 to a backend condition
  (network, 401 unauth, 403 forbidden, 404 not found, 409 conflict, 429
  rate-limited, 5xx service unavailable). Show the backend's `error`
  string when present.
- Accessibility is non-negotiable: no divs pretending to be buttons.

---

## 12. Handoff

When done, create `frontend/HANDOFF.md` listing:
- exact test counts (unit test suites + tests + pass/fail),
- pages implemented,
- known gaps + follow-ups (E2E, CI, cookie-based refresh, i18n, etc.),
- any deviations from this brief and why,
- screenshots (or ASCII wireframes) of the 4 main pages.

Commit on branch `frontend-mvp`:
`git commit -m "frontend: react/vite MVP for the shortener"`
Push the branch, do not open a PR — the owner will review.

Reply with the branch name + `pnpm test` output tail + `pnpm build` output
tail + the list from HANDOFF.md.
