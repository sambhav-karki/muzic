package com.butterzhub.muzic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "playlist_songs")
@Getter @Setter
public class PlaylistSong {
    @Id @GeneratedValue private UUID id;
    // @ManyToOne records ownership while LAZY defers loading the parent.
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Playlist playlist;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String artist;
    @Column(nullable = false) private String youtubeVideoId;
    @Column(length = 2048) private String thumbnailUrl;
    @Column(nullable = false) private Integer position;
}
