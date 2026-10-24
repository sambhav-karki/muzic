package com.butterzhub.muzic.service;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Discovery remains inside the original library's artist space, including after repeated skips. */
@Service
public class SwipeCurationService {
    // A conservative, fixed neighbor set for the narrow grunge-library case. Never expanded from skips.
    private static final List<String> GRUNGE_NEIGHBORS = List.of("Nirvana", "Pearl Jam", "Soundgarden", "Alice in Chains", "Stone Temple Pilots", "Foo Fighters", "The Smashing Pumpkins", "Pixies");
    private final PlaylistService playlists;
    private final YouTubeClient youtube;
    private static class ArtistPool {
        final String name;
        final Deque<SongDto> tracks = new ArrayDeque<>();
        int page; String token; boolean exhausted;
        ArtistPool(String name) { this.name = name; }
    }
    private static class Session {
        final String owner;
        final long created = System.currentTimeMillis();
        final LinkedHashMap<String, ArtistPool> artists = new LinkedHashMap<>();
        final Map<String, SongDto> issued = new HashMap<>();
        final Set<String> titles = new HashSet<>();
        Session(String owner) { this.owner = owner; }
    }
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    public SwipeCurationService(PlaylistService playlists, YouTubeClient youtube) { this.playlists = playlists; this.youtube = youtube; }
    public record Start(String sessionId, SongDto seed, String anchorName) {}
    private static String owner(OAuth2AuthenticationToken auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connect Google to start your discovery session.");
        return auth.getAuthorizedClientRegistrationId() + ":" + auth.getName();
    }
    public Start start(OAuth2AuthenticationToken auth) {
        String owner = owner(auth);
        var remote = playlists.importYouTubePlaylists(auth);
        var anchor = remote.stream().max(Comparator.comparingInt(PlaylistDto::itemCount)).orElse(null);
        List<SongDto> tracks = anchor == null ? playlists.likedMusic(auth) : playlists.importYouTubeTracks(auth, anchor.id());
        if (tracks.isEmpty()) tracks = playlists.likedMusic(auth);
        var eligible = tracks.stream().filter(TrackIdentity::valid).filter(song ->
            !Set.of("youtube", "variousartists", "music").contains(TrackIdentity.artist(song.artist()))).toList();
        if (eligible.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Add music to your YouTube playlists or likes to start a vibe session.");
        Session session = new Session(owner);
        for (SongDto song : eligible) {
            var pool = session.artists.computeIfAbsent(TrackIdentity.artist(song.artist()), key -> new ArtistPool(song.artist()));
            pool.tracks.add(song);
        }
        if (session.artists.size() == 1 && GRUNGE_NEIGHBORS.stream().map(TrackIdentity::artist).anyMatch(session.artists::containsKey)) {
            for (String artist : GRUNGE_NEIGHBORS) session.artists.putIfAbsent(TrackIdentity.artist(artist), new ArtistPool(artist));
        }
        SongDto seed = eligible.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(eligible.size()));
        session.issued.put(seed.youtubeVideoId(), seed); session.titles.add(TrackIdentity.title(seed.title()));
        sessions.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().created > 1800000);
        if (sessions.size() >= 1000) sessions.keySet().stream().findFirst().ifPresent(sessions::remove);
        String id = UUID.randomUUID().toString(); sessions.put(id, session);
        return new Start(id, seed, anchor == null ? "Your liked music" : anchor.title());
    }
    public SongDto next(OAuth2AuthenticationToken auth, String sessionId, String videoId, String distance, Set<String> excluded) {
        String owner = owner(auth);
        if (!Set.of("close", "divergent").contains(distance)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Distance must be close or divergent.");
        Session session = sessionId == null ? sessions.values().stream().filter(s -> s.owner.equals(owner)).max(Comparator.comparingLong(s -> s.created)).orElse(null) : sessions.get(sessionId);
        if (session == null || !session.owner.equals(owner) || System.currentTimeMillis() - session.created > 1800000)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vibe session expired. Restart the deck.");
        synchronized (session) {
            SongDto current = session.issued.get(videoId);
            if (current == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Track does not belong to this vibe session.");
            String currentArtist = TrackIdentity.artist(current.artist());
            var pools = new ArrayList<>(session.artists.entrySet());
            int origin = 0;
            for (int i = 0; i < pools.size(); i++) if (pools.get(i).getKey().equals(currentArtist)) origin = i;
            // Close stays with this artist; divergent rotates through the fixed anchor artists.
            int start = distance.equals("divergent") && pools.size() > 1 ? (origin + 1) % pools.size() : origin;
            int attempts = distance.equals("close") ? 1 : pools.size();
            for (int offset = 0; offset < Math.min(attempts, 8); offset++) {
                var entry = pools.get((start + offset) % pools.size()); ArtistPool pool = entry.getValue();
                for (int pageAttempt = 0; pageAttempt <= 3; pageAttempt++) {
                    while (!pool.tracks.isEmpty()) {
                        SongDto candidate = pool.tracks.removeFirst();
                        if (!TrackIdentity.valid(candidate) || excluded.contains(candidate.youtubeVideoId()) || session.issued.containsKey(candidate.youtubeVideoId())
                            || session.titles.contains(TrackIdentity.title(candidate.title())) || !TrackIdentity.artist(candidate.artist()).equals(entry.getKey())) continue;
                        session.issued.put(candidate.youtubeVideoId(), candidate); session.titles.add(TrackIdentity.title(candidate.title())); return candidate;
                    }
                    if (pool.exhausted || pageAttempt == 3) break;
                    try {
                        var result = youtube.searchPage(pool.name + " official audio", pool.page, 9, pool.token);
                        pool.tracks.addAll(result.tracks()); pool.page++; pool.token = result.nextPageToken();
                        pool.exhausted = pool.page >= result.totalPages() && (pool.token == null || pool.token.isBlank());
                    } catch (RuntimeException failure) { break; }
                }
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No unseen tracks remain in this vibe. Retry or restart from your library.");
        }
    }
}
