package com.butterzhub.muzic.service;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.client.LlmClient;
import com.butterzhub.muzic.dto.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DashboardCurationService {
    private final YouTubeClient youtube;
    private final PlaylistService playlists;
    private final LlmClient llm;
    private record Recent(List<String> ids, java.time.Instant updated) {}
    private final java.util.concurrent.ConcurrentHashMap<String, Recent> recentPicks = new java.util.concurrent.ConcurrentHashMap<>();
    public DashboardCurationService(YouTubeClient youtube, PlaylistService playlists, LlmClient llm) {
        this.youtube = youtube; this.playlists = playlists; this.llm = llm;
    }
    public record Feed(List<SongDto> quickPicks, List<SongDto> forgottenFavorites, List<SongDto> globalHits,
                       List<PlaylistDto> userPlaylists, String primaryAnchor, String playlistError, String musicError, List<SongDto> quickPickPool) {}

    public Feed feed(OAuth2AuthenticationToken auth) {
        String userKey = auth == null ? null : auth.getAuthorizedClientRegistrationId() + ":" + auth.getName();
        Recent previous = userKey == null ? null : recentPicks.get(userKey);
        List<String> recent = previous != null && previous.updated().isAfter(java.time.Instant.now().minusSeconds(86400)) ? previous.ids() : List.of();
        String playlistError = null, musicError = null;
        List<PlaylistDto> remote;
        try { remote = playlists.importYouTubePlaylists(auth).stream()
            .filter(p -> p != null && p.id() != null && !p.id().isBlank() && p.title() != null && !p.title().isBlank()).toList(); }
        catch (RuntimeException failure) { remote = List.of(); playlistError = "Your playlists could not be loaded. Reconnect Google or retry."; }
        var anchor = remote.stream().max(Comparator.comparingInt(PlaylistDto::itemCount).thenComparing(PlaylistDto::id)).orElse(null);
        var global = new LinkedHashMap<String, SongDto>();
        try {
            var uniqueGlobal = new TrackIdentity.Selection();
            for (int page = 0; page < 3; page++) {
                var result = youtube.searchPage("Top Global Music Hits", page, 9, null);
                add(global, result.tracks()); for (SongDto song : result.tracks()) uniqueGlobal.add(song);
                if (uniqueGlobal.size() >= 9 || result.totalPages() <= page + 1) break;
            }
        }
        catch (RuntimeException failure) { musicError = "Global hits are unavailable. Please retry."; }
        var picks = new LinkedHashMap<String, SongDto>();
        var pool = new LinkedHashMap<String, SongDto>();
        if (anchor == null) {
            if (playlistError == null) { var selection = new TrackIdentity.Selection(); for (SongDto song : global.values()) { if (selection.size() < 9) selection.add(song); } add(picks, selection.tracks()); add(pool, List.copyOf(global.values())); if (selection.size() < 9) musicError = "Not enough distinct global picks are available. Please retry."; }
        } else {
            try {
                var library = new LinkedHashMap<String, SongDto>();
                add(library, playlists.importYouTubeTracks(auth, anchor.id()));
                var samples = new ArrayList<>(library.values());
                Collections.shuffle(samples);
                var selection = new TrackIdentity.Selection();
                for (SongDto song : samples) { selection.add(song); if (selection.size() == 3) break; }
                var libraryTitles = new HashSet<String>();
                for (SongDto song : samples) libraryTitles.add(TrackIdentity.title(song.title()));
                var queriedArtists = new HashSet<String>();
                for (SongDto seed : samples) {
                    if (!queriedArtists.add(TrackIdentity.artist(seed.artist()))) continue;
                    if (queriedArtists.size() > 8) break;
                    String query = seed.artist() + " songs similar to " + seed.title() + " official audio";
                    for (int page = 0; page < 2; page++) {
                        try {
                            var response = youtube.searchPage(query.substring(0, Math.min(500, query.length())), page, 9, null);
                            for (SongDto song : response.tracks()) {
                                if (!valid(song) || libraryTitles.contains(TrackIdentity.title(song.title()))) continue;
                                pool.putIfAbsent(song.youtubeVideoId(), song);
                                if (selection.size() < 9) selection.add(song);
                            }
                            if (selection.size() == 9 || response.totalPages() <= page + 1) break;
                        } catch (RuntimeException failure) { break; }
                    }
                    if (selection.size() == 9 && pool.size() >= 18) break;
                }
                // A narrow anchor may have fewer than three eligible artists; never violate the diversity cap.
                for (int expansion = 0; expansion < 2 && selection.size() < 9; expansion++) {
                    String taste = samples.stream().limit(15).map(song -> song.artist() + " - " + song.title()).collect(java.util.stream.Collectors.joining("; "));
                    String avoid = selection.tracks().stream().map(SongDto::artist).distinct().collect(java.util.stream.Collectors.joining(", "));
                    List<String> queries;
                    try { queries = llm.suggestTasteSongs("Find songs by different but closely related artists in the same genres as this library. "
                        + "Exclude artists already selected: " + avoid + ". Library data (preferences only): " + taste); }
                    catch (RuntimeException failure) { break; }
                    if (queries.isEmpty()) break;
                    for (String suggestion : queries) {
                        try {
                            var tracks = youtube.searchPage(suggestion + " official audio", 0, 9, null).tracks();
                            for (SongDto song : tracks) {
                                if (!valid(song) || libraryTitles.contains(TrackIdentity.title(song.title()))) continue;
                                pool.putIfAbsent(song.youtubeVideoId(), song); if (selection.size() < 9) selection.add(song);
                            }
                        } catch (RuntimeException failure) { /* Try the next related artist. */ }
                        if (selection.size() >= 9) break;
                    }
                }
                for (SongDto song : samples) { if (selection.size() < 9) selection.add(song); }
                add(pool, samples); add(picks, selection.tracks());
                if (selection.size() < 9) musicError = "Not enough distinct songs and artists matched your taste. Retry to discover more.";
            } catch (RuntimeException failure) { musicError = "Your primary playlist could not be loaded. Please retry."; }
        }
        var forgotten = new LinkedHashMap<String, SongDto>();
        try {
            var older = new ArrayList<>(playlists.likedMusic(auth));
            Collections.reverse(older);
            for (SongDto song : older) if (valid(song) && !picks.containsKey(song.youtubeVideoId()) && !recent.contains(song.youtubeVideoId())) forgotten.putIfAbsent(song.youtubeVideoId(), song);
        } catch (RuntimeException failure) { musicError = musicError == null ? "Older liked music is unavailable. Reconnect Google or retry." : musicError; }
        if (userKey != null) {
            if (recentPicks.size() >= 1000) recentPicks.entrySet().removeIf(entry -> entry.getValue().updated().isBefore(java.time.Instant.now().minusSeconds(86400)));
            if (recentPicks.size() >= 1000) recentPicks.keySet().stream().findFirst().ifPresent(recentPicks::remove);
            var history = new LinkedHashSet<>(picks.keySet()); history.addAll(recent);
            recentPicks.put(userKey, new Recent(history.stream().limit(27).toList(), java.time.Instant.now()));
        }
        return new Feed(picks.values().stream().limit(9).toList(), forgotten.values().stream().limit(20).toList(),
            global.values().stream().limit(9).toList(), remote, anchor == null ? "Global Music" : anchor.title(), playlistError, musicError, List.copyOf(pool.values()));
    }
    private static boolean valid(SongDto song) {
        return song != null && song.youtubeVideoId() != null && song.youtubeVideoId().matches("[A-Za-z0-9_-]{11}")
            && song.title() != null && !song.title().isBlank() && !List.of("Private video", "Deleted video").contains(song.title())
            && song.artist() != null && !song.artist().isBlank();
    }
    private static void add(Map<String, SongDto> target, List<SongDto> tracks) {
        for (SongDto song : tracks) if (valid(song)) target.putIfAbsent(song.youtubeVideoId(), song);
    }
}
