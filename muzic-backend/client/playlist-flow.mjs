// Create one flow per curated results list. Keep it for retries; recreate it when songs change.
export function createPlaylistFlow({ baseUrl = '', fetchImpl = globalThis.fetch, onState = () => {} } = {}) {
  let savedId;
  let pending;
  let completed;
  const emit = state => onState(state);
  async function post(path, body) {
    const response = await fetchImpl(`${baseUrl}${path}`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.detail || `Request failed (${response.status})`);
    return data;
  }
  async function run(name, songs) {
    try {
      if (!savedId) {
        if (!songs?.length) throw new Error('No verified songs to save. Try another recommendation.');
        emit({ phase: 'saving', loading: true, message: 'Saving playlist…' });
        const saved = await post('/api/playlists', { name, songs });
        if (!saved.id) throw new Error('Playlist response is missing its ID.');
        savedId = saved.id;
      }
      emit({ phase: 'syncing', loading: true, playlistId: savedId, message: 'Syncing to YouTube…' });
      const result = await post(`/api/playlists/${encodeURIComponent(savedId)}/sync-youtube`);
      if (result.synced !== true || !result.youtubePlaylistId) throw new Error('YouTube sync is incomplete. Retry to resume.');
      completed = { phase: 'success', loading: false, playlistId: savedId,
        youtubePlaylistId: result.youtubePlaylistId,
        url: `https://www.youtube.com/playlist?list=${encodeURIComponent(result.youtubePlaylistId)}`,
        message: 'Playlist synced to YouTube.' };
      emit(completed);
      return completed;
    } catch (error) {
      emit({ phase: 'error', loading: false, playlistId: savedId,
        message: savedId ? `Playlist saved. Sync failed: ${error.message} Retry to resume.` : error.message });
      throw error;
    }
  }
  return {
    saveAndSync(name, songs) {
      if (completed) return Promise.resolve(completed);
      if (pending) return pending;
      pending = run(name, songs).finally(() => { pending = undefined; });
      return pending;
    }
  };
}
