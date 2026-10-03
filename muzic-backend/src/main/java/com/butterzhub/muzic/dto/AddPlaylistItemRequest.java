package com.butterzhub.muzic.dto;
import jakarta.validation.constraints.*;
public record AddPlaylistItemRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{11}") String videoId) {}
