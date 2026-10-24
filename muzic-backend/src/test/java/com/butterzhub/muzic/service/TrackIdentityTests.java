package com.butterzhub.muzic.service;
import com.butterzhub.muzic.dto.SongDto;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackIdentityTests {
    @Test void lithiumVersionsShareOneIdentity() {
        for (String title : java.util.List.of("Lithium", "Lithium (Remastered 2011)", "Lithium [Deluxe Edition]", "Lithium (Live at Reading)",
            "Lithium (Devonshire Mix)", "Nirvana - Lithium (Official Music Video)", "Lithium - Devonshire Mix", "Lithium [Official Audio]"))
            assertEquals("lithium", TrackIdentity.title(title), title);
    }
    @Test void selectionRejectsDifferentUploadsAndCapsCanonicalArtistAtTwo() {
        var selection = new TrackIdentity.Selection();
        assertTrue(selection.add(new SongDto("Lithium", "Nirvana", "aaaaaaaaaaa", null)));
        assertFalse(selection.add(new SongDto("Lithium (Remastered)", "NirvanaVEVO", "bbbbbbbbbbb", null)));
        assertTrue(selection.add(new SongDto("Come As You Are", "Nirvana - Topic", "ccccccccccc", null)));
        assertFalse(selection.add(new SongDto("In Bloom", "NirvanaVEVO", "ddddddddddd", null)));
        assertFalse(selection.add(new SongDto("Another Song", "Pearl Jam", "aaaaaaaaaaa", null)));
        assertTrue(selection.add(new SongDto("Alive", "Pearl Jam", "eeeeeeeeeee", null)));
    }
}
