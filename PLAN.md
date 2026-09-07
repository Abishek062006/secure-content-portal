# Secure Content Portal — Implementation Plan

Internship screening assignment. Java Spring Boot backend (mandated), everything else chosen for
reliability on free tiers and for defensibility in the follow-up interview.

---

## 1. Stack

| Layer | Choice | Why this one |
|---|---|---|
| Backend | Spring Boot 3.5.16, Java 21 | Your constraint. 3.5.x over 4.1 because Spring Security 6 idioms are what you'll find when you search for help, and you'll be questioned on this code. |
| Views | Thymeleaf + Tailwind + HTMX + Alpine.js | Same-origin. The HttpOnly session cookie and the OAuth redirect are correct *by construction* — no CORS, no `SameSite=None`, one deployable instead of two. |
| Auth | Spring Security `oauth2-client` → server-side session | Brief bans localStorage tokens. Sessions in Postgres via Spring Session JDBC so they survive free-tier restarts. |
| Database | Neon Postgres | Auto-resumes in ~500ms. Supabase free *projects* pause after 7 days idle — a real risk if the reviewer opens your link late. |
| File storage | Supabase Storage, private bucket, S3 API | 1 GB free, no credit card. Behind a `StorageService` interface so swapping to R2 later is a one-class change. |
| PDF | Apache PDFBox, server-side rasterisation | The PDF binary never reaches the browser. This is a genuine boundary, not a deterrent — the main differentiator on the 20% criterion. |
| Type checking | Apache Tika | Magic-byte sniffing. Extension and `Content-Type` are both attacker-controlled. |
| HTML sanitising | jsoup | Strips scripts/handlers at upload, before the bytes are ever stored. |
| Hosting | Render free web service, Docker | Builds in the cloud, so you don't need Docker locally. |

---

## 2. Rubric → where it gets earned

| Weight | Criterion | Phase |
|---|---|---|
| 20% | Content protection | Phase 5 |
| 15% | Auth & access control | Phase 2, tests in Phase 7 |
| 15% | Security practices | Phase 3 + Phase 5 |
| 15% | Admin CRUD | Phase 4 |
| 15% | Free-tier deployment | Phase 8 |
| 10% | Code quality & architecture | every phase closes with one meaningful commit |
| 5% | UI/UX & responsiveness | Phase 6 |
| 5% | Documentation | Phase 8 |

---

## 3. Architecture

```
Browser
  │  session cookie (HttpOnly, Secure, SameSite=Lax)
  ▼
Render — single Spring Boot service
  ├── Thymeleaf pages         server-rendered, role-aware
  ├── Spring Security         Google OIDC → role from OUR database
  ├── Admin controllers       hasRole('ADMIN'), CSRF-protected POSTs
  ├── Stream layer            HMAC ticket → range streaming
  ├── PDF renderer            PDFBox page → watermarked JPEG
  └── HTML sandbox            jsoup-sanitised → CSP sandbox iframe
        │                            │
        ▼                            ▼
   Neon Postgres            Supabase Storage (private)
   metadata, roles,         opaque UUID keys,
   sessions, audit          no public URL exists
```

The browser never learns a storage key, never receives a storage credential, and never gets a URL
that outlives its session.

---

## Phase 0 — Accounts and credentials

**You do this. ~45 minutes, no code.** Nothing later works without it.

### 0.1 Google OAuth
1. [console.cloud.google.com](https://console.cloud.google.com) → new project `Secure Content Portal`
2. **APIs & Services → OAuth consent screen** → External. App name, your email as support + developer contact.
3. Scopes: `openid`, `email`, `profile` only.
4. **Publish the app.** Leaving it in *Testing* means only emails you list can sign in — your reviewer would be locked out. These three scopes are non-sensitive, so publishing needs **no Google verification**.
5. **Credentials → Create credentials → OAuth client ID → Web application**
   - Authorised redirect URI: `http://localhost:8080/login/oauth2/code/google`
   - (The Render URI gets added in Phase 8.)
6. Save the **Client ID** and **Client Secret**.

### 0.2 Neon Postgres
1. [neon.tech](https://neon.tech) → sign up → new project, region matching your Render region (Oregon or Frankfurt).
2. Copy the **pooled** connection string.
3. Split it into JDBC form: `jdbc:postgresql://<host>/<db>?sslmode=require`, plus user and password separately.

### 0.3 Supabase Storage
1. [supabase.com](https://supabase.com) → new project.
2. **Storage → New bucket** named `content`. **Public toggle OFF.** This is the whole point.
3. **Project Settings → Storage → S3 Connection** → generate an access key. Note the endpoint, region, access key ID and secret.

> Free tier caps individual files at 50 MB. Use short sample videos for the demo.

### 0.4 GitHub and Render
1. New GitHub repo `secure-content-portal`.
2. [render.com](https://render.com) → sign up with GitHub. Don't create a service yet.

### Deliverable
A local `.env` (gitignored) holding all of the above, and a committed `.env.example` with the same
keys and empty values.

---

## Phase 1 — Skeleton that boots

**Goal:** `mvn spring-boot:run` starts, connects to Neon, Flyway builds the schema.

### Files
| File | Purpose |
|---|---|
| `pom.xml` | Dependencies, Java 21 ✅ *already written* |
| `SecurePortalApplication.java` | Entry point ✅ |
| `config/AppProperties.java` | Admin allow-list, ticket secret + TTL, PDF limits ✅ |
| `config/StorageProperties.java` | S3 endpoint, bucket, credentials ✅ |
| `user/{User,Role,UserRepository}.java` | User entity, VIEWER/ADMIN enum ✅ |
| `content/{ContentItem,ContentType,ContentRepository}.java` | Content entity + per-type limits ✅ |
| `db/migration/V1__init.sql` | 4 app tables + 2 Spring Session tables ✅ |
| `application.yml` | Local config, env-var placeholders | 
| `application-prod.yml` | Production overrides |
| `.gitignore`, `.env.example`, `run-local.sh` | Env plumbing |

### Schema
`users` · `content_items` · `content_views` · `audit_logs` · `spring_session` · `spring_session_attributes`

### Acceptance
- App starts with no stack trace
- Neon console shows all six tables plus `flyway_schema_history`
- Restart is clean (migrations are idempotent)

**Commit:** `chore: scaffold Spring Boot app with schema and configuration`

---

## Phase 2 — Google OAuth and role enforcement

**Goal:** sign in with Google; first login = VIEWER; allow-listed email = ADMIN; admin routes reject viewers server-side.

### Files
| File | Purpose |
|---|---|
| `auth/AppPrincipal.java` | Session principal — serializable scalars only, never the JPA entity ✅ |
| `auth/AppOidcUserService.java` | Provisions the user, assigns role from *our* DB ✅ |
| `config/SecurityConfig.java` | **The core file.** URL rules, CSRF, headers, session policy |
| `auth/AuthController.java` | `/` and `/login` |
| `templates/login.html` | Google sign-in button, no password form |
| `templates/layout.html` | Shell: nav, role badge, avatar |
| `templates/error/{403,404,500}.html` | No blank screens |

### Access rules
| Path | Rule |
|---|---|
| `/`, `/login`, `/css/**`, `/js/**`, `/error` | permit all |
| `/admin/**` | `hasRole('ADMIN')` |
| everything else | authenticated |

Plus `@EnableMethodSecurity` and `@PreAuthorize` on admin service methods — defence in depth, so a
future controller mistake can't leak an admin operation.

### Role assignment
Read from the `users` table, seeded on first login from the `APP_ADMIN_EMAILS` allow-list. The
OAuth response never influences the role. Promotion re-checks on every login (so you can seed an
admin after the account exists); demotion is deliberately a manual DB action.

### Acceptance
- Your admin email → Admin badge in the header
- A second Google account → Viewer
- As that viewer, hit `/admin/content` directly → **403**, not a redirect to a hidden page
- DevTools → `SESSION` cookie shows HttpOnly ✓; localStorage empty ✓
- `SELECT * FROM spring_session` returns a row

**Commit:** `feat(auth): Google OAuth sign-in with server-side role enforcement`

---

## Phase 3 — Storage and upload validation

**Goal:** bytes land in a private bucket under an unguessable key; bad files rejected with a clear message.

### Files
| File | Purpose |
|---|---|
| `storage/StorageService.java` | Interface: `put`, `openStream(key, start, end)`, `delete`, `exists` |
| `storage/S3StorageService.java` | AWS SDK v2, path-style addressing |
| `config/StorageConfig.java` | `S3Client` bean |
| `common/FileValidator.java` | Tika magic bytes + extension + size + per-type allow-list |
| `common/UploadException.java` | Carries a user-facing message |
| `common/GlobalExceptionHandler.java` | `MaxUploadSizeExceededException` → friendly page, not a stack trace |

### Validation matrix
| Type | Extensions | Detected MIME must be | Max |
|---|---|---|---|
| VIDEO | `.mp4` `.webm` | `video/mp4`, `video/webm` | 512 MB *(Supabase caps at 50 MB)* |
| PDF | `.pdf` | `application/pdf` | 32 MB |
| HTML | `.html` `.htm` | `text/html`, `text/plain`, `application/xhtml+xml` | 2 MB |

Three independent checks, all required. **Tika reads the actual leading bytes** — a `.exe` renamed
to `.mp4` fails, where an extension check or a `Content-Type` check alone would pass it.

### Storage keys
`content/{uuid4}/{random}` — zero user input in the path. No traversal, no guessing, no collision.

### Acceptance
- Rename a `.txt` to `.mp4`, upload → rejected with a readable error
- A real MP4 → object visible in the Supabase bucket
- Hitting the bucket's public URL for that object → **403**

**Commit:** `feat(storage): private S3-compatible storage with magic-byte upload validation`

---

## Phase 4 — Admin CRUD

**Goal:** upload, edit metadata, delete — reliably, for all three content types.

### Files
| File | Purpose |
|---|---|
| `content/AdminContentController.java` | List, new, create, edit, update, delete |
| `content/ContentService.java` | Transactional — storage and DB move together |
| `content/dto/{UploadForm,EditForm}.java` | Bean Validation constraints |
| `templates/admin/{list,upload,edit}.html` | Admin screens |
| `templates/fragments/{nav,alerts,confirm-modal}.html` | Shared UI |

### Delete safety
POST only (never GET), CSRF token required, and a modal that names the item being deleted with a
visually distinct destructive button. Two deliberate actions, per the brief.

### Acceptance
- Create one video, one PDF, one HTML item
- Edit each one's title/description/category
- Delete one → **both** the DB row and the storage object are gone
- Try the delete URL as a GET → nothing happens

**Commit:** `feat(admin): upload, edit and delete flows for all three content types`

---

## Phase 5 — Protected delivery ★ the 20%

**Goal:** a viewer with devtools open cannot obtain a reusable file URL.

### Files
| File | Purpose |
|---|---|
| `stream/StreamTicket.java` | Record: contentId, userId, sessionBinding, purpose, expiry, nonce |
| `stream/StreamTicketService.java` | HMAC-SHA256 sign/verify, Base64URL, constant-time compare |
| `stream/StreamController.java` | `/api/stream/{ticket}` with `Range` support |
| `stream/RangeRequest.java` | Parse and validate `Range: bytes=a-b` |
| `pdf/PdfRenderService.java` | PDFBox page → watermarked JPEG, cached |
| `pdf/PdfPageController.java` | `/api/pdf/{ticket}/page/{n}` |
| `html/HtmlSanitizer.java` | jsoup safelist |
| `html/HtmlController.java` | Sandboxed HTML with CSP |

### What the ticket binds, and what each binding stops
| Binding | Stops |
|---|---|
| HMAC signature | Forging a ticket for a different item |
| Expiry (5 min; 30 min for video) | A copied URL working later |
| **Session hash** | A copied URL working in another browser — *this is the strong one* |
| Content ID | Reusing one item's ticket for another |
| Purpose | Using a PDF-page ticket to pull the raw file |
| Role re-checked per request | A demoted user keeping access |

Expiry alone is what most submissions do. Session binding is what makes a leaked URL useless
*immediately*, to *everyone else*, not just eventually.

### Per content type
- **Video** — proxied range streaming. `Accept-Ranges: bytes`, honours `Range`, so seeking works. Storage credentials never reach the browser.
- **PDF** — PDFBox renders page *N* at 110 DPI, overlays the viewer's email as a faint diagonal watermark, returns a JPEG, caches it to `derived/{id}/p{n}.jpg`. **The PDF binary never leaves the server.** Contrast with PDF.js, where the whole file sits in browser memory and can be pulled straight back out.
- **HTML** — jsoup strips `script`, `iframe`, `object`, `embed`, `form` and all `on*` handlers at upload. Served with `Content-Security-Policy: sandbox; default-src 'none'` into `<iframe sandbox>` *without* `allow-same-origin`, giving it a null origin that cannot touch your session.

All delivery responses carry `Cache-Control: no-store`, `Content-Disposition: inline`,
`X-Content-Type-Options: nosniff`.

### Acceptance — do these yourself, they're the demo
- Copy the video stream URL from the Network tab → open in a private window → **403**
- Wait past expiry, reload it in the *same* browser → **403 expired**
- View a PDF, watch the Network tab → only `.jpg` requests, **zero** `application/pdf` responses
- `curl` the Supabase object URL directly → **denied**

**Commit:** `feat(delivery): session-bound stream tickets, range streaming and server-side PDF rendering`

---

## Phase 6 — Viewer experience

**Goal:** clean, responsive, no admin controls anywhere, no blank screens.

### Files
| File | Purpose |
|---|---|
| `content/ViewerContentController.java` | `/library`, `/content/{id}` |
| `templates/library.html` | Responsive card grid, type badges, empty state |
| `templates/view/{video,pdf,html}.html` | Three players |
| `static/js/pdf-viewer.js` | Page nav, lazy loading, zoom — talks only to our image endpoint |
| `static/js/video-player.js` | Ticket refresh, `controlsList="nodownload"` |
| `static/css/app.css` | Tailwind build (generated locally, committed — keeps the Docker build node-free) |

### Video ticket lifetime — a decision to make
A 5-minute ticket expires mid-video. Two options:
- **(Recommended)** 30-minute TTL, still session-bound. Session binding is the real control; TTL is secondary. Simple, no moving parts.
- Silent refresh: player fetches a new ticket near expiry and swaps the source preserving playback position. Stronger, but fiddly and can glitch on seek.

Start with the first; upgrade if time allows.

### Responsiveness
Verify at 375 px, 768 px and 1280 px. Every list has an empty state; every fetch has a loading state; every failure has a message.

**Commit:** `feat(viewer): responsive library and inline players for video, PDF and HTML`

---

## Phase 7 — Bonus features and tests

Four of the brief's five bonuses are nearly free given the schema is already there.

| Feature | Work |
|---|---|
| Search & filter | Repository query exists; wire to the library with HTMX live results |
| View counts / last viewed | Increment on view, admin-only column |
| Audit log | `audit/{AuditLog,AuditRepository,AuditService}.java` + `/admin/audit` |
| Access-control tests | **The one that proves the 15% criterion** |

### Test suite
- `AccessControlTest` — every admin route × {anonymous, viewer, admin}, asserting 302 / 403 / 200
- `StreamTicketServiceTest` — tampered signature, expired ticket, cross-session reuse, wrong purpose
- `FileValidatorTest` — spoofed extensions, oversized files, disallowed types

**Commit:** `feat: search, view tracking, audit log and access-control test suite`

---

## Phase 8 — Deploy and document

### Files
`Dockerfile` (multi-stage: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`) ·
`.dockerignore` · `application-prod.yml` · `README.md` · optional `.github/workflows/keepalive.yml`

### Render
1. New Web Service → connect the repo → **Docker** runtime → **Free** plan
2. Environment variables: `SPRING_PROFILES_ACTIVE=prod`, DB creds, Google creds, storage creds, `APP_TICKET_SECRET` (fresh 32-byte random), `APP_ADMIN_EMAILS`
3. `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k` — 512 MB is tight
4. Health check path `/healthz`

### Then
- Add `https://<app>.onrender.com/login/oauth2/code/google` to the Google OAuth client
- Confirm the consent screen is **published**

### The gotcha that breaks most deployments
Render terminates TLS upstream, so without `server.forward-headers-strategy=native` Spring builds
the OAuth redirect as `http://` and Google rejects it. Set it in `application-prod.yml`. Also:
cookie `secure=true`, HSTS on.

### README — the brief names these sections
Setup and run · environment variables · which free services and **why** · architecture overview ·
**security trade-offs**: what's a real boundary vs a deterrent, and what you'd add with more time.

Be honest in that last one. Real: server-side role checks, private storage, session-bound expiring
tickets, PDF never leaving the server. Deterrents only: right-click blocking, hidden controls, the
video watermark overlay. And note that anything renderable is screen-recordable — saying so is
worth more marks than pretending otherwise.

### Acceptance — full cold run
Incognito → live URL → Google sign-in → browse → play a video → read a PDF → open an HTML item →
confirm no admin controls are visible → confirm `/admin/**` returns 403.

**Commit:** `chore: production Dockerfile, Render deployment and documentation`

---

## 4. Risk register

| Risk | Mitigation |
|---|---|
| Render 512 MB + PDFBox spike | Cap DPI at 110, cap pages at 300, render one page at a time, cache results |
| Render cold start 40–60 s | Documented in README (the brief explicitly allows it); optional keepalive cron |
| Supabase pauses after 7 days idle | Keepalive ping; note in README; one-click restore |
| Supabase 50 MB file cap | Use short sample videos |
| Google consent screen left in Testing | **Publish it** — reviewer can't sign in otherwise |
| `forward-headers-strategy` unset | Set in `application-prod.yml`; symptom is an OAuth `redirect_uri_mismatch` |
| Neon connection limits | Pooled connection string, HikariCP max 5 |

---

## 5. Timeline

| Day | Phases |
|---|---|
| 1 | 0, 1, 2 — accounts, skeleton, working Google login with roles |
| 2 | 3, 4 — storage, validation, full admin CRUD |
| 3 | 5 — protected delivery (the heaviest phase; give it a full day) |
| 4 | 6, 7 — viewer UX, responsive pass, bonuses, tests |
| 5 | 8 — deploy, security pass, README, submit |

Deploy on day 5 as scheduled, but do a **throwaway deploy at the end of day 1** with nothing but
the login page. Finding the `forward-headers` and redirect-URI problems on day 1 costs an hour;
finding them on day 5 costs your deadline.

---

## 6. Current state

Phase 1 files marked ✅ above already exist on disk, along with `auth/AppPrincipal.java` and
`auth/AppOidcUserService.java` from Phase 2. `git init` has been run; **no commits yet**. Nothing
external has been touched — no accounts, no deploys.
