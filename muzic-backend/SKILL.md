# Muzic System Design and Architecture Contract

## Purpose and Workspace Baseline

Muzic is a dual-tier full-stack music curation application: a browser frontend and a Spring Boot backend. The backend follows three logical layers; these layers do not imply three independently deployed services.

The current workspace contains a Maven backend under `muzic-backend/`, configured for Java 21 and Spring Boot, with base package `com.butterzhub.muzic`. The workspace currently contains an application bootstrap, configuration properties, a context-loading test, an end-to-end recommendation flow (`RecommendController` -> `RecommendationService`), and external integration clients (`LlmClient` for Gemini-based structured generation and `YouTubeClient` for metadata resolution via official YouTube Data API v3).

This document is the implementation contract. It defines intended behavior and technical boundaries. Implementation must proceed in discrete, verified micro-steps.

## 1. System Architecture & Layering

### Deployment and Request Flow

- Recommendation Flow: Browser frontend → REST controller → application service → external integration adapter (`LlmClient`, `YouTubeClient`) → client receives curated `SongDto` items.
- Playback Flow: Browser frontend → official embedded YouTube IFrame API. The backend returns metadata and video identifiers; the browser embeds the selected videos.
- OAuth2 & Sync Flow: Browser frontend → Spring Security OAuth2 Authorization Endpoint → Google OAuth2 Consent → Backend token exchange & user sync → `YouTubeSyncService` (delegated user calls via OAuth2 access token).

### Tier 1: Controller Layer — HTTP Boundary

- Use `@RestController` for JSON API endpoints.
- Bind request bodies and query parameters to typed DTOs, applying validation at the HTTP boundary (`@Valid`, `@NotBlank`).
- Use `ResponseEntity` to express HTTP status, headers, and typed response bodies.
- Delegate all curation, matching, orchestration, persistence, and playlist retrieval to application services.
- Return RFC 7807 problem details through Spring `ProblemDetail` with `application/problem+json` for errors via `@RestControllerAdvice`.
- Keep provider payloads, credentials, prompt construction, retries, and persistence entities out of controllers.

### Tier 2: Service Layer — Application and Business Logic

- Own the recommendation workflow and LLM prompt engineering for curated song outputs.
- Treat the user's prompt as input data. Keep application instructions separate and require structured title/artist output; the LLM must not invent YouTube IDs or thumbnail URLs.
- Validate the model response, reject malformed candidates, and deduplicate songs before resolving videos.
- Orchestrate YouTube searches, select appropriate embeddable video matches, and preserve curation order.
- Obtain `youtubeVideoId` and `thumbnailUrl` from official YouTube API metadata.
- Provide deterministic fallback handling on external provider outages.
- Own OAuth2 playlist retrieval and synchronization logic, authorization requirements, provider failure translation, and operation-level timeout budgets.
- Manage database transactions using `@Transactional` at the service boundary. Never leak JPA entities directly to the presentation tier; map entities to typed DTOs.

### Tier 3: Integration Layer — External Provider Adapters

- Encapsulate LLM and YouTube HTTP clients behind dedicated adapters (`LlmClient`, `YouTubeClient`, `YouTubeSyncClient`).
- Own provider authentication, request serialization, response parsing, timeouts, and transport error handling via Spring Boot 3 `RestClient`.
- Use header-based authentication (`X-goog-api-key`) for Google Generative Language API calls.
- Use query-parameter or header API key authentication for public YouTube Data API v3 queries.
- Use user-delegated Bearer access tokens (`Authorization: Bearer <token>`) for authenticated YouTube Data API operations (e.g., creating playlists, inserting playlist items).
- Translate provider responses into internal immutable values; avoid exposing provider-specific schemas through public APIs.

### Tier 4: Persistence Tier — PostgreSQL & Spring Data JPA

- Manage relational persistence via PostgreSQL and Spring Data JPA.
- Relational schema requirements:
  - `User`: Primary key `UUID id`, unique `googleId`, `email`, `name`, `pictureUrl`.
  - `Playlist`: Primary key `UUID id`, `@ManyToOne(fetch = FetchType.LAZY) User user`, `name`, `youtubePlaylistId` (nullable, populated on sync), `createdAt` (`Instant`).
  - `PlaylistSong`: Primary key `UUID id`, `@ManyToOne(fetch = FetchType.LAZY) Playlist playlist`, `title`, `artist`, `youtubeVideoId`, `thumbnailUrl`, `position` (`Integer`).
- Repositories must extend `JpaRepository` and expose targeted queries (`findByGoogleId`, `findByUserId`).
- Database modifications must be transactional and use standard dialect-compatible constraints.

### Client Integration — Embedded YouTube Playback

- Use the official frontend YouTube IFrame API with backend-provided video IDs.
- Perform zero media scraping, audio extraction, proxy streaming, or media downloading.
- Preserve YouTube player controls, attribution, embedding restrictions, and required player behavior. Follow applicable YouTube API terms and policies.
- Configure CORS for approved frontend origins (e.g., `http://localhost:4200`). Never expose backend API keys, OAuth2 client secrets, or refresh tokens to the browser.

## 2. API & Security Contracts

All REST routes are rooted at `/api`. Successful JSON responses use `application/json`; error responses use `application/problem+json`.

### Authentication & OAuth2 Endpoints

- Handled via `spring-boot-starter-oauth2-client`.
- Provider: Google Identity Platform.
- Scopes: `openid`, `profile`, `email`, `https://www.googleapis.com/auth/youtube`.
- Authentication Mechanism: Standard OAuth2 authorization code flow with secure session/cookie or token exchange.
- Token Retention: Persist user identifiers and manage OAuth2 authorized client tokens to execute delegated YouTube operations.

### POST /api/recommend

**Purpose:** Curate and resolve songs from a natural-language mood, genre, activity, or preference prompt.

**Request:** `{ "prompt": string }`

**Success:** `200 OK` with a JSON array of objects, each shaped as `{ "title": string, "artist": string, "youtubeVideoId": string, "thumbnailUrl": string }`.

| Field | Contract |
| --- | --- |
| `title` | Non-blank song title. |
| `artist` | Non-blank artist display name. |
| `youtubeVideoId` | Verified video identifier returned by YouTube, suitable for an attempted embed. |
| `thumbnailUrl` | HTTPS thumbnail URL obtained from YouTube metadata. |

### POST /api/playlists

**Purpose:** Save a curated playlist to the user's account in PostgreSQL.

**Authorization:** Authenticated user session.

**Request:** `{ "name": string, "songs": [ { "title": string, "artist": string, "youtubeVideoId": string, "thumbnailUrl": string } ] }`

**Success:** `201 Created` returning the saved playlist DTO with its generated `id` and item count.

### POST /api/playlists/{id}/sync-youtube

**Purpose:** Export a saved local playlist to the authenticated user's personal YouTube account.

**Authorization:** Authenticated user session with authorized `https://www.googleapis.com/auth/youtube` scope.

**Success:** `200 OK` returning `{ "playlistId": string, "youtubePlaylistId": string, "synced": true }`.

### GET /api/youtube/playlists

**Purpose:** Retrieve the authenticated user's own YouTube playlists through delegated OAuth2 access.

**Authorization:** Authenticated application session associated with the user's Google OAuth2 grant.

**Success:** `200 OK` with `{ "items": [ { "playlistId": string, "title": string, "description": string, "thumbnailUrl": string | null } ], "nextPageToken": string | null }`.

### Shared Error Contract

Problem responses contain `type`, `title`, `status`, `detail`, and `instance` (RFC 7807).

| Status | Intended condition |
| --- | --- |
| `400` | Invalid body, malformed JSON, missing or blank required input, or validation constraint failure. |
| `401` | Missing, invalid, or expired session/authentication token. |
| `403` | Insufficient delegated OAuth2 scopes (e.g., missing YouTube write permission). |
| `404` | Target resource (playlist, user) not found. |
| `429` | Rate limit reached or upstream API quota exhausted. |
| `502` | Invalid upstream response from Google AI Studio or YouTube API. |
| `503` | Upstream provider unavailability. |
| `500` | Unexpected internal server error. |

## 3. Enterprise Engineering Rules

- Use Java 21 Records for immutable request, response, and external integration DTOs.
- Keep transport DTOs separate from JPA entities and external API schemas. Never use JPA entities as controller arguments or return values.
- Maintain packages beneath `com.butterzhub.muzic`:
  - `.controller`: Web ingress, request validation, HTTP status mapping.
  - `.service`: Business logic, transaction orchestration, domain transformations.
  - `.client`: Outbound REST integrations (`RestClient`).
  - `.model`: JPA entities.
  - `.repository`: Spring Data JPA interfaces.
  - `.dto`: Immutable Java records for API transport.
  - `.exception`: Global exception advice and custom domain exceptions.
  - `.config`: Security, CORS, and client bean configurations.
- Use constructor injection exclusively across all components.
- Secure secrets: Google OAuth client secrets, Gemini API keys, and YouTube API keys must be loaded via environment variables or external configuration, never hardcoded.

## 4. Codex Micro-Step Directive

- Generate only a single-class snippet or configuration block at a time.
- Begin each step with the file path, responsibility, and contract requirement being satisfied.
- Include concise educational comments explaining annotations (`@Entity`, `@Table`, `@ManyToOne`, `@Transactional`).
- For dependency additions (`pom.xml`) or configuration changes (`application.properties`), provide an isolated micro-step prior to application code.
- Verify each step with compilation checks (`./mvnw clean compile`) before proceeding to downstream components.