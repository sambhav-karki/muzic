package com.butterzhub.muzic.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// @Entity persists playlists; LAZY ownership avoids loading the user for every row.
@Entity
@Table(name = "playlists")
@Getter @Setter
public class Playlist {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;
    @Column(nullable = false) private String name;
    private String youtubePlaylistId;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private int syncedTrackCount;
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlaylistSong> songs = new ArrayList<>();
}
