package com.butterzhub.muzic.dto;

// Records expose immutable API values without exposing provider payloads or JPA entities.
public record PlaylistDto(String playlistId, String title, String description, String thumbnailUrl) {}
