# Recommendation results → YouTube playlist

This workspace has no frontend application. `playlist-flow.mjs` is a browser ES module
that implements the client API contract for a recommendation results component.
Import or copy it into the frontend bundle; it is not served by the backend.

Create one flow for each curated results list and bind both **Save Playlist** and
**Sync to YouTube** to `saveAndSync(name, songs)`. Pass the current curated `SongDto[]`
unchanged. Use the same flow on retry; create a new flow when the list changes.
The module awaits `POST /api/playlists` and then immediately calls
`POST /api/playlists/{saved.id}/sync-youtube`, with session cookies on both requests.
It shares in-flight requests, disables duplicate saves after success, and retains
the saved ID on sync failure so a retry resumes the existing playlist.

Example component integration (elements belong to the frontend):

```js
import { createPlaylistFlow } from './playlist-flow.mjs';

const flow = createPlaylistFlow({
  baseUrl: 'http://localhost:8080', // Use your configured backend origin.
  onState(state) {
    saveButton.disabled = syncButton.disabled = state.loading || state.phase === 'success';
    resultsPanel.setAttribute('aria-busy', String(state.loading));
    statusBanner.setAttribute('role', state.phase === 'error' ? 'alert' : 'status');
    statusBanner.textContent = state.message;
    if (state.url) {
      const link = document.createElement('a');
      link.href = state.url;
      link.textContent = 'Open playlist on YouTube';
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      statusBanner.append(' ', link);
    }
  }
});
const save = () => flow.saveAndSync(nameInput.value, curatedSongs).catch(() => {});
saveButton.addEventListener('click', save);
syncButton.addEventListener('click', save);
```

Sign in via `/oauth2/authorization/google` first. The backend currently allows the
frontend origin `http://localhost:4200`; configure the actual deployment origin as needed.
Do not put provider keys or tokens in the frontend. Problem Details `detail` is shown
on failures, including authentication and scope failures. Empty recommendations disable
saving and should offer another prompt. A complete recommendation lookup outage returns
an empty array, rather than an unverified static song.

Recommendation IDs are 11-character YouTube IDs whose existence, public/processed
status and embedding permission were checked through `videos.list`; thumbnails are
valid HTTPS URLs from that video's metadata. Verification is at recommendation time:
a video may be removed or restricted before sync. The existing sync provider handling
continues to cover that race.

Run client contract tests with `node --test client/playlist-flow.test.mjs`.
