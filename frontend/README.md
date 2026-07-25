# Distributed URL Shortener - Frontend

This is the frontend single-page application (SPA) for the Distributed URL Shortener project.

## Technologies

- **Framework**: React 19 + Vite + TypeScript
- **Styling**: Tailwind CSS (v3) + shadcn/ui
- **State Management**: Zustand (Auth) + React Query (API data caching)
- **Routing**: React Router v7
- **Forms & Validation**: React Hook Form + Zod
- **Testing**: Vitest + Testing Library + MSW

## Getting Started

1. **Install dependencies**:
   ```bash
   npm install
   ```

2. **Run development server**:
   ```bash
   npm run dev
   ```
   The application proxy is configured in `vite.config.ts` to forward `/api` and short URLs to the backend at `http://localhost:8080` (or the URL specified by `VITE_API_BASE_URL`).

3. **Build for production**:
   ```bash
   npm run build
   ```

4. **Run tests**:
   ```bash
   npm run test
   ```

## Key Features

- JWT Authentication (Memory stored access token, localStorage refresh token)
- Automatic token rotation via Axios interceptors
- Centralized 429 Rate Limit handling via Axios interceptors + Toast notifications
- Clean UI using `shadcn/ui` components
- Responsive Link Dashboard and Statistics charting with `recharts`
