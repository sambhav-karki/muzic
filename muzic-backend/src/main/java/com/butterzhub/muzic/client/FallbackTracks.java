package com.butterzhub.muzic.client;

import com.butterzhub.muzic.dto.SongDto;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared rotating pool for provider outages; successful model suggestions take priority. */
public final class FallbackTracks {
    private FallbackTracks() {}
    private static final AtomicInteger sequence = new AtomicInteger();
    private static SongDto song(String artist, String title, String id) {
        return new SongDto(title, artist, id, "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg");
    }
    private static final List<SongDto> POOL = List.of(
        song("Metallica", "Enter Sandman", "CD-E-LDc384"),
        song("Daft Punk", "Get Lucky", "5NV6Rdv1a3I"),
        song("The Weeknd", "Blinding Lights", "4NRXx6U8ABQ"),
        song("Coldplay", "Yellow", "yKNxeF4KMsY"),
        song("OneRepublic", "Counting Stars", "hT_nvWreIhg"),
        song("Nirvana", "Smells Like Teen Spirit", "hTWKbfoikeg"),
        song("Michael Jackson", "Billie Jean", "Zi_XLOBDo_Y"),
        song("Adele", "Rolling in the Deep", "rYEDA3JcQqw"),
        song("Pharrell Williams", "Happy", "ZbZSe6N_BXs"),
        song("Dua Lipa", "Levitating", "TUVcZfQe-Kw"),
        song("Linkin Park", "Numb", "kXYiU_JCYtU"),
        song("Gorillaz", "Feel Good Inc.", "HyHNuVaZJ-k"),
        song("Earth, Wind & Fire", "September", "Gs069dndIYk"),
        song("Fleetwood Mac", "Dreams", "mrZRURcb1cM"),
        song("Queen", "Don't Stop Me Now", "HgzGwKwLmgM"),
        song("Outkast", "Hey Ya!", "PWgvGjAhvIw"),
        song("a-ha", "Take On Me", "djV11Xbc914"),
        song("Toto", "Africa", "FTQbiNvZqaY"));

    public static List<SongDto> forPrompt(String prompt) {
        int start = Math.floorMod((prompt == null ? 0 : prompt.hashCode()) + sequence.getAndAdd(3), POOL.size());
        var songs = new ArrayList<SongDto>();
        for (int i = 0; i < POOL.size(); i++) songs.add(POOL.get((start + i) % POOL.size()));
        return List.copyOf(songs);
    }
    public static List<String> queries(String prompt) {
        return forPrompt(prompt).stream().limit(3).map(s -> s.artist() + " - " + s.title()).toList();
    }
}
