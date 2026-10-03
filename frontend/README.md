# Muzic frontend

Retro React + TypeScript UI with search, a YouTube player, music discovery, and a local Swiped playlist.

## Run

Use Node 22.12+ (Node 24 recommended for the built-in TypeScript test runner).

```powershell
cd frontend
npm ci
Copy-Item .env.example .env
npm run dev
```

Open http://localhost:4200. Start Spring Boot separately on http://localhost:8080 with Java 21 and PostgreSQL at localhost:5432/muzic. Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, YOUTUBE_API_KEY, and GEMINI_API_KEY in the backend environment. The database defaults to the local `muzic` database with username/password `postgres`; override these with SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, and SPRING_DATASOURCE_PASSWORD. Configure Google's OAuth redirect as http://localhost:8080/login/oauth2/code/google.

For deployment, set VITE_API_BASE_URL to the backend's public URL in Vercel before building, and set FRONTEND_URL to the frontend's origin in the backend environment (without a trailing slash). FRONTEND_URL controls the OAuth success redirect and is allowed by CORS alongside http://localhost:4200. Register the deployed backend's `/login/oauth2/code/google` URL with Google. Build the backend container with `muzic-backend` as the Docker build context; it listens on port 8080.

```powershell
npm run build
npm run lint
npm test
```

## Backend contract

All frontend API calls include session credentials. Account state comes from GET /api/me, because HTTP-only session cookies cannot be reliably checked from JavaScript.

- POST /api/recommend with JSON { "prompt": "your vibe" }: verified Song objects.
- GET /api/playlists: signed-in user's saved playlists.
- GET /api/discovery/trending: public YouTube mostPopular music chart (category 10, US), filtered for public, processed, embeddable videos. Requires YOUTUBE_API_KEY. Errors describe missing configuration or quota/provider failure; no static substitute tracks.
- GET /oauth2/authorization/google: Google login.

Songs contain title, artist, youtubeVideoId, and optional thumbnailUrl. Google account names are displayed from profile metadata; Swiped storage uses the stable profile.id.

## Behavior and limitations

Splash intro runs once per browser session. Player Next wraps playlists (or shuffles to another track); standalone playback consumes its recommendation queue and requests similar tracks when exhausted. YouTube owns playback and may block particular videos, autoplay, or embedding at runtime; the visible CRT preview and player status expose these cases.

Swipe the card horizontally, click Like/Pass, or focus the deck and use arrow keys. Short or vertical gestures preserve scrolling. Guests can preview/pass tracks and must connect Google to save likes. Signed-in likes live only in this browser's localStorage under a profile-specific Swiped key. They are never sent to playlist save/sync endpoints or synchronized to YouTube. Clearing browser storage removes them. Blocked storage retains new likes only in memory and shows a message.

The personal feed requests recommendations from the latest five likes after a short debounce; stale requests are aborted on new likes or account changes. Feed recommendations are not automatically saved. Trending is a US music chart rather than a worldwide chart. External Google Fonts require network access; monospace is the fallback. Live OAuth, recommendation services, and YouTube playback require valid backend credentials and network access.

## Browser smoke tests

```powershell
npx playwright install chromium
npm run test:e2e
```

Browser smoke tests mock backend API responses and the YouTube IFrame API. They validate UI flows, responsive layout, and local playlist behavior; they do not validate live OAuth, Gemini, YouTube quota, or real audio playback.
