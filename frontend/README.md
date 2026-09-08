# Secure Content Portal — frontend

React (Vite) single-page app for the Secure Content Portal. Talks to the Spring Boot backend
in the repo root over `/api/**` — see that project's README for the backend and deployment.

## Local development

```bash
npm install
npm run dev
```

Runs on `http://localhost:5173` and expects the backend at `http://localhost:8080`
(see `.env.development`). Sign-in redirects to the backend's
`/oauth2/authorization/google`, and cookies/CSRF are handled in `src/api.js` —
no tokens are stored client-side.

## Build

```bash
npm run build
```

`.env.production` points `VITE_API_URL` at the deployed backend. Update it (and the
backend's `APP_FRONTEND_URL` / CORS origin) if either URL changes.
