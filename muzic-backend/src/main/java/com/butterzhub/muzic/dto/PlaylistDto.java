package com.butterzhub.muzic.dto;
public record PlaylistDto(String id, String title, String description, String thumbnailUrl, int itemCount) {
 public PlaylistDto(String id, String title, String description, String thumbnailUrl) { this(id,title,description,thumbnailUrl,0); }
}
