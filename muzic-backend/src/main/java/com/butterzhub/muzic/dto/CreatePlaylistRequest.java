package com.butterzhub.muzic.dto;
import jakarta.validation.constraints.*;
public record CreatePlaylistRequest(@NotBlank @Size(max=150) String title,
 @Size(max=5000) String description, @Pattern(regexp="private|public|unlisted") String privacyStatus) {}
