package com.butterzhub.muzic.dto;

import java.util.UUID;

public record SavedPlaylistDto(UUID id, String name, int itemCount, String youtubePlaylistId) {}
