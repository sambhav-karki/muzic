# Muzic System Design and Architecture Contract

## Purpose and Workspace Baseline

Muzic is a dual-tier full-stack music curation application: a browser frontend and a Spring Boot backend. The backend follows three logical layers; these layers do not imply three independently deployed services.

The current workspace contains a Maven backend under `muzic-backend/`, configured for Java 21 and Spring Boot 4.1.1, with base package `com.butterzhub.muzic`. It currently provides an application bootstrap, application properties, and a context-loading test. Frontend, LLM integration, YouTube integration, and OAuth2 implementation remain planned work.

This document is the implementation contract. It defines intended behavior without supplying application implementation code. Dependency additions and implementation must occur only in subsequent requested micro-steps.

## 1. System Architecture & 3-Tier Layering

### Deployment and Request Flow

Browser frontend → REST controller → application service → external integration adapter → LLM provider or official YouTube Data API.

Playback follows a separate path: browser frontend → official embedded YouTube IFrame API. The backend returns metadata and video identifiers; the browser embeds the selected videos.

### Tier 1: Controller Layer — HTTP Boundary

- Use `@RestController` for JSON API endpoints.
- Bind request bodies and query parameters to typed DTOs, applying validation at the HTTP boundary.
- Use `ResponseEntity` to express HTTP status, headers, and typed response bodies.
- Delegate all curation, matching, orchestration, and playlist retrieval to application services.
- Return RFC 7807 problem details through Spring `ProblemDetail` with `application/problem+json` for errors.
- Keep provider payloads, credentials, prompt construction, retries, and business decisions out of controllers.

### Tier 2: Service Layer — Application and Business Logic

- Own the recommendation workflow and LLM prompt engineering for exactly 10 distinct songs.
- Treat the user's prompt as input data. Keep application instructions separate and require structured title/artist output; the LLM must not invent YouTube IDs or thumbnail URLs.
- Validate the model response, reject malformed candidates, and deduplicate songs before resolving videos.
- Orchestrate YouTube searches, select appropriate embeddable video matches, and preserve the final curation order.
- Obtain `youtubeVideoId` and `thumbnailUrl` from official YouTube API metadata.
- Use bounded retries and replacement candidates when a song cannot be resolved. A successful recommendation returns exactly 10 resolved songs; exhaustion produces a problem response instead of silently returning an incomplete list.
- Own OAuth2 playlist retrieval decisions, authorization requirements, provider failure translation, and operation-level timeout budgets.

### Tier 3: Integration Layer — External Provider Adapters

- Encapsulate LLM and YouTube HTTP clients behind dedicated interfaces and adapters.
- Own provider authentication, request serialization, response parsing, timeouts, and transport error handling.
- Translate provider responses into internal immutable values; avoid exposing provider-specific schemas through public APIs.
- Use bounded concurrency and quota-aware retries. Retry only transient failures within the operation budget; avoid blind retries for authorization failures or exhausted quota.
- Keep provider secrets and OAuth2 tokens server-side, supplied through environment configuration or a secret store.
- No persistence tier is required by this initial contract. Any future storage must have its own repository boundary and an explicit data-retention design.

### Client Integration — Embedded YouTube Playback

- Use the official frontend YouTube IFrame API with backend-provided video IDs.
- Perform zero media scraping, audio extraction, proxy streaming, or media downloading.
- Preserve YouTube player controls, attribution, embedding restrictions, and required player behavior. Follow applicable YouTube API terms and policies; using an iframe alone does not establish compliance.
- Handle unavailable videos and embedding restrictions gracefully in the frontend.
- Configure CORS for approved frontend origins. Never expose backend API keys or OAuth2 refresh tokens to the browser.

## 2. API Contracts

All routes are rooted at `/api`. Successful JSON responses use `application/json`; error responses use `application/problem+json`. Required strings must be non-null and non-blank after trimming. Define finite input-length and request-rate limits during implementation and document them before release.

### POST /api/recommend

**Purpose:** Curate and resolve 10 songs from a natural-language mood, genre, activity, or preference prompt.

**Request:** `{ "prompt": string }`

**Success:** `200 OK` with a JSON array of exactly 10 objects, each shaped as `{ "title": string, "artist": string, "youtubeVideoId": string, "thumbnailUrl": string }`.

| Field | Contract |
| --- | --- |
| `title` | Non-blank song title. |
| `artist` | Non-blank artist display name. |
| `youtubeVideoId` | Verified video identifier returned by YouTube, suitable for an attempted embed. |
| `thumbnailUrl` | HTTPS thumbnail URL obtained from YouTube metadata. |

Results are distinct by song identity and video ID. Do not add a response envelope or provider-only fields. Request processing completes within a bounded timeout and returns a problem response when 10 valid results cannot be produced.

### GET /api/youtube/search?query=...

**Purpose:** Search the official YouTube Data API for music video candidates.

**Request:** Required URL-encoded `query` parameter containing a non-blank search string.

**Success:** `200 OK` with a bounded JSON array using the same `{ title, artist, youtubeVideoId, thumbnailUrl }` shape as recommendations. No matches returns `[]`. Return video results only and apply available embedding filters.

Search titles and artist attribution may be ambiguous. Use the video's title and a best-effort artist attribution; when attribution is unavailable, use the channel display name. Do not represent inferred attribution as verified song metadata. A search result does not guarantee future playback availability.

### GET /api/youtube/playlists

**Purpose:** Retrieve the authenticated user's own YouTube playlists through delegated OAuth2 access.

**Authorization:** Require an authenticated application session associated with the user's Google OAuth2 grant and the minimum read-only YouTube scope needed. Keep the authorization flow, callback, token refresh, and encrypted token storage behind a dedicated security boundary. Finalize those routes before implementing this endpoint.

**Request:** No required query parameters. Permit optional `pageToken` for opaque provider pagination.

**Success:** `200 OK` with `{ "items": [ { "playlistId": string, "title": string, "description": string, "thumbnailUrl": string | null } ], "nextPageToken": string | null }`.

Return only playlists owned by the authenticated user. An empty collection returns `items: []`. Preserve opaque pagination tokens; do not expose access or refresh tokens. Missing playlist thumbnails are represented by `null`.

### Shared Error Contract

Problem responses contain `type`, `title`, `status`, `detail`, and `instance`. Use stable problem identifiers; ensure `status` agrees with the HTTP status. Optional extensions may include a safe `traceId` and field-validation errors. Never expose stack traces, credentials, raw provider payloads, or sensitive prompt content.

| Status | Intended condition |
| --- | --- |
| `400` | Invalid body, malformed JSON, missing or blank required input, or invalid pagination input. |
| `401` | Missing or expired application authentication for user playlist access. |
| `403` | Insufficient delegated scope or denied access to an authenticated operation. |
| `429` | Application request-rate limit reached; include `Retry-After` when known. |
| `502` | Invalid upstream response or exhausted curation resolution attempts. |
| `503` | Temporary provider unavailability or provider quota exhaustion; include `Retry-After` when known. |
| `504` | Upstream operation exceeded its timeout budget. |
| `500` | Unexpected internal failure, with a safe generic detail. |

Translate provider-specific status codes according to these application meanings; a backend API-key failure must not appear as a user's authentication failure.

## 3. Enterprise Engineering Rules

- Use Java 21 Records for immutable request, response, and integration DTOs. Defensively copy collection components where necessary; records alone do not make nested mutable values immutable.
- Use `@RestControllerAdvice` for global exception handling, consistent `ProblemDetail` construction, and validation error translation.
- Enforce strict separation of concerns: controllers handle HTTP; services handle business logic; integration adapters handle external transport. Controllers must contain no business logic.
- Use constructor injection and configuration properties for explicit dependencies and environment-specific settings.
- Keep transport DTOs separate from provider schemas and future persistence entities. Maintain packages beneath `com.butterzhub.muzic` organized by responsibility.
- Validate and constrain untrusted input, LLM output, and provider responses. Do not execute generated content or accept user-supplied provider URLs.
- Protect API keys and OAuth2 tokens, redact sensitive logs, and bind delegated access to the correct authenticated user.
- Provide structured logs and correlation identifiers for diagnosis; record latency and failure categories without logging secrets or sensitive prompt content by default.
- Verify service behavior with isolated provider substitutes, controller contracts with HTTP-boundary tests, and integration parsing/error handling with controlled fixtures. Cover the exact-10 invariant, deduplication, failed matching, validation, OAuth2 failures, pagination, quota handling, and timeouts as those behaviors are implemented.
- Do not add frameworks, dependencies, storage, or unrelated features merely to satisfy speculative future needs. Confirm required capabilities against the workspace's Spring Boot and Java baseline before each implementation step.

## 4. Codex Micro-Step Directive

- Generate only a single-class snippet at a time. Treat one record, interface, or other top-level Java type as one micro-step.
- Begin each micro-step with the file path, its responsibility, and the relevant contract it satisfies.
- Include concise educational comments explaining annotations and framework methods where they appear, such as `@RestController`, request binding, `ResponseEntity`, and `@RestControllerAdvice`. Explain their purpose without narrating obvious syntax.
- Keep every snippet narrowly scoped and reviewable. State required dependencies and assumptions; do not silently generate supporting classes or an entire feature.
- For a build or configuration change, provide a separate focused micro-step instead of combining it with multiple application classes.
- Provide a brief, relevant verification step and identify any unresolved dependency on subsequent micro-steps.
- Preserve this architecture and the public API shapes across steps. Explicitly identify any proposed contract change before implementing it.
- The present task authorizes this blueprint only. Do not generate application implementation code until the user requests the next implementation micro-step.
