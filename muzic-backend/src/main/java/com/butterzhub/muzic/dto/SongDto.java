package com.butterzhub.muzic.dto;

// Immutable song metadata shared by the client, service, and HTTP response.
public record SongDto(
        String title,
        String artist,
        String youtubeVideoId,
        String thumbnailUrl
) {
}
