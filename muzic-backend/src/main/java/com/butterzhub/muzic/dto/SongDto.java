package com.butterzhub.muzic.dto;

// A Java record is a data carrier with final components and generated accessors,
// constructor, equals, hashCode, and toString methods.
// Records reduce DTO boilerplate in Spring Boot and support JSON serialization;
// these String components make SongDto immutable.
public record SongDto(
        String title,
        String artist,
        String youtubeVideoId,
        String thumbnailUrl
) {
}
