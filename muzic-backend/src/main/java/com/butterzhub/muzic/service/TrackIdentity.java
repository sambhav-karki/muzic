package com.butterzhub.muzic.service;

import com.butterzhub.muzic.dto.SongDto;
import java.text.Normalizer;
import java.util.*;

public final class TrackIdentity {
    private TrackIdentity() {}
    public static String title(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
            .replace("&amp;", "and").replace("&#39;", "").replace("&quot;", "")
            .replaceAll("\\([^)]*(?:remaster|deluxe|live|mix|version|edit|official|audio|lyric)[^)]*\\)|\\[[^]]*(?:remaster|deluxe|live|mix|version|edit|official|audio|lyric)[^]]*\\]", " ")
            .replaceAll("\\s[-–—]\\s[^–—-]*(?:mix|remaster|live|version|deluxe|edit).*$", " ")
            .replaceAll("^.*?\\s[-–—]\\s", "")
            .replaceAll("\\b(?:official(?: music)? video|lyric(?:s)?(?: video)?|official audio|audio|remastered?|deluxe(?: edition)?|\\d{4} remaster(?:ed)?)\\b", " ")
            .replaceAll("\\b(?:19|20)\\d{2}\\b", " ")
            .replaceAll("[^\\p{L}\\p{N} ]", "").replaceAll("\\s+", " ").trim();
    }
    public static String artist(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
            .replaceAll("(?:vevo|official|topic)\\b", "").replaceAll("[^\\p{L}\\p{N}]", "");
    }
    public static boolean valid(SongDto song) {
        return song != null && song.youtubeVideoId() != null && song.youtubeVideoId().matches("[A-Za-z0-9_-]{11}")
            && !title(song.title()).isBlank() && !artist(song.artist()).isBlank()
            && !List.of("private video", "deleted video").contains(title(song.title()));
    }
    public static final class Selection {
        private final Set<String> ids = new HashSet<>(), titles = new HashSet<>();
        private final Map<String, Integer> artists = new HashMap<>();
        private final List<SongDto> tracks = new ArrayList<>();
        public boolean add(SongDto song) {
            if (!valid(song) || ids.contains(song.youtubeVideoId()) || titles.contains(title(song.title())) || artists.getOrDefault(artist(song.artist()), 0) >= 2) return false;
            ids.add(song.youtubeVideoId()); titles.add(title(song.title())); artists.merge(artist(song.artist()), 1, Integer::sum); tracks.add(song); return true;
        }
        public int size() { return tracks.size(); }
        public List<SongDto> tracks() { return List.copyOf(tracks); }
    }
}
