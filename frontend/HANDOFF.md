# Deployment Plan and Production Readiness

The Distributed URL Shortener project is now split into two clean components:

1. **Backend**: Go + Chi + Redis + PostgreSQL
2. **Frontend**: React + Vite + Tailwind v3

## Production Readiness Overview

### Backend Checklist
- Ensure `DATABASE_URL` and `REDIS_ADDR` are properly set in the production environment.
- Setup a reverse proxy (e.g. Nginx, Caddy) or an Ingress controller to terminate TLS and forward traffic to the Go server on port `8080`.
- Rate limiting is configured in memory per instance by default. For a distributed setup, ensure the rate limiter uses Redis instead (the backend code provides interfaces that can be extended for distributed rate limiting if required).

### Frontend Checklist
- Configure `VITE_API_BASE_URL` at build time so the Vite app knows where the backend API lives (e.g., `https://api.yourdomain.com`).
- The frontend is a static SPA. It should be built via `npm run build` and the resulting `dist/` folder can be served by any static file server (Nginx, AWS S3 + CloudFront, Vercel, Netlify).
- If serving from Nginx or similar, ensure that all unknown routes fallback to `index.html` (e.g. `try_files $uri $uri/ /index.html`) so React Router can handle client-side routing.
- The `dist/` folder size is small, and assets are properly chunked and minified by Rollup (Vite).

## Running in Production

**Option 1: Docker Compose (Single Node)**
A `docker-compose.prod.yml` can be created to spin up the PostgreSQL DB, Redis, the Go Backend container, and an Nginx container serving the static Frontend `dist/` files.

**Option 2: Cloud / Serverless**
- **Frontend**: Deploy directly to Vercel/Netlify. Connect the GitHub repo and set the build command to `npm run build` and output directory to `dist`. Set the environment variable `VITE_API_BASE_URL` to point to the backend deployment.
- **Backend**: Deploy the Go binary or Docker container to AWS ECS, Google Cloud Run, or a basic VPS. Ensure it can connect to managed database instances (RDS/Cloud SQL) and ElastiCache/MemoryStore for Redis.

## Important Note on Shortened Links
When users visit shortened URLs (e.g. `yourdomain.com/abc1234`), they should ideally hit the backend server directly to minimize latency. 
In the current setup, Vite acts as a proxy for `/[0-9a-zA-Z_-]{1,32}` to redirect to `localhost:8080`. In production:
- Your reverse proxy (Nginx) should be configured to route paths matching short codes directly to the Go backend.
- Or, host the frontend on a subdomain (e.g., `app.yourdomain.com`) and the short links on the root domain (`yourdomain.com/xyz`), pointing the root domain directly to the Go backend API.
