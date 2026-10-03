package com.butterzhub.muzic.service;

import com.butterzhub.muzic.client.YouTubeSyncClient;
import com.butterzhub.muzic.dto.PlaylistDto;
import com.butterzhub.muzic.dto.PlaylistDetailDto;
import com.butterzhub.muzic.dto.SongDto;
import com.butterzhub.muzic.dto.SavePlaylistRequest;
import com.butterzhub.muzic.dto.SavedPlaylistDto;
import com.butterzhub.muzic.model.Playlist;
import com.butterzhub.muzic.model.PlaylistSong;
import com.butterzhub.muzic.model.User;
import com.butterzhub.muzic.repository.PlaylistRepository;
import com.butterzhub.muzic.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
// @Transactional defines the database unit of work at the application boundary.
@Transactional
public class PlaylistService {
    private final PlaylistRepository playlists;
    private final UserRepository users;
    private final YouTubeSyncClient youtube;

    public PlaylistService(PlaylistRepository playlists, UserRepository users, YouTubeSyncClient youtube) {
        this.playlists = playlists;
        this.users = users;
        this.youtube = youtube;
    }

    private User user(OAuth2AuthenticationToken auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login required");
        String subject = auth.getPrincipal().getAttribute("sub");
        return users.findByGoogleId(subject).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public UUID authenticatedUserId(OAuth2AuthenticationToken auth) { return user(auth).getId(); }

    @Transactional(readOnly = true)
    public List<PlaylistDetailDto> list(OAuth2AuthenticationToken auth) {
        return playlists.findByUserId(user(auth).getId()).stream()
            .sorted(java.util.Comparator.comparing(Playlist::getCreatedAt).reversed())
            .map(p -> new PlaylistDetailDto(p.getId(), p.getName(), p.getYoutubePlaylistId(),
                p.getSongs().stream().map(s -> new SongDto(s.getTitle(), s.getArtist(),
                    s.getYoutubeVideoId(), s.getThumbnailUrl())).toList())).toList();
    }

    public SavedPlaylistDto save(OAuth2AuthenticationToken auth, SavePlaylistRequest request) {
        Playlist playlist = new Playlist();
        playlist.setUser(user(auth));
        playlist.setName(request.name());
        for (int position = 0; position < request.songs().size(); position++) {
            var input = request.songs().get(position);
            PlaylistSong song = new PlaylistSong();
            song.setPlaylist(playlist);
            song.setPosition(position);
            song.setTitle(input.title());
            song.setArtist(input.artist());
            song.setYoutubeVideoId(input.youtubeVideoId());
            song.setThumbnailUrl(input.thumbnailUrl());
            playlist.getSongs().add(song);
        }
        playlists.save(playlist);
        return new SavedPlaylistDto(playlist.getId(), playlist.getName(), playlist.getSongs().size(), null);
    }

    // Remote writes cannot roll back: retain confirmed inserts when a later request fails.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public SyncResult syncToYouTube(OAuth2AuthenticationToken auth, UUID id) {
        Playlist playlist = playlists.findByIdAndUserId(id, user(auth).getId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        if (playlist.getYoutubePlaylistId() == null) {
            playlist.setYoutubePlaylistId(youtube.createPlaylist(auth, playlist.getName(), "Curated with Muzic"));
            playlists.flush();
        }
        while (playlist.getSyncedTrackCount() < playlist.getSongs().size()) {
            if (System.nanoTime() >= deadline)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sync budget reached; retry to resume");
            PlaylistSong song = playlist.getSongs().get(playlist.getSyncedTrackCount());
            youtube.addTrackToPlaylist(auth, playlist.getYoutubePlaylistId(), song.getYoutubeVideoId());
            // The resume cursor includes unavailable videos deliberately skipped by the client.
            playlist.setSyncedTrackCount(playlist.getSyncedTrackCount() + 1);
        }
        return new SyncResult(id, playlist.getYoutubePlaylistId(), true);
    }

    public PlaylistDto createYouTubePlaylist(OAuth2AuthenticationToken auth, com.butterzhub.muzic.dto.CreatePlaylistRequest request) {
        user(auth);
        return youtube.createPlaylist(auth, request.title().trim(), request.description() == null ? "" : request.description(),
            request.privacyStatus() == null ? "private" : request.privacyStatus());
    }
    public void addYouTubeTrack(OAuth2AuthenticationToken auth, String playlistId, String videoId) {
        user(auth);
        youtube.insertTrack(auth, playlistId, videoId);
    }
    public List<SongDto> importYouTubeTracks(OAuth2AuthenticationToken auth, String playlistId) {
        user(auth);
        return youtube.fetchPlaylistItems(auth, playlistId);
    }
    public List<PlaylistDto> importYouTubePlaylists(OAuth2AuthenticationToken auth) {
        User user = user(auth);
        List<PlaylistDto> remote = youtube.fetchUserPlaylists(auth);
        user.setYoutubeHistoryContext(remote.stream().limit(30)
            .map(p -> text(p.title()) + ": " + text(p.description()))
            .collect(Collectors.joining("\n")));
        return remote;
    }

    @Transactional(readOnly = true)
    public String historyContext(UUID userId) {
        User user = users.findById(userId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String saved = playlists.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream()
            .flatMap(p -> p.getSongs().stream()).limit(50)
            .map(s -> text(s.getArtist()) + " - " + text(s.getTitle()))
            .collect(Collectors.joining("\n"));
        return "Recently saved tracks:\n" + saved + "\nImported playlist interests:\n"
            + (user.getYoutubeHistoryContext() == null ? "" : user.getYoutubeHistoryContext());
    }

    private static String text(String input) {
        if (input == null) return "";
        String clean = input.replaceAll("[\\p{Cntrl}]", " ");
        return clean.substring(0, Math.min(clean.length(), 120));
    }

    public record SyncResult(UUID playlistId, String youtubePlaylistId, boolean synced) {}
}
