package com.butterzhub.muzic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SavePlaylistRequest(
    @NotBlank @Size(max = 150) String name,
    @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Track> songs
) {
    public record Track(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String artist,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{11}") String youtubeVideoId,
        @Size(max = 2048) @Pattern(regexp = "https://[^\\s]+") String thumbnailUrl
    ) {}
}
