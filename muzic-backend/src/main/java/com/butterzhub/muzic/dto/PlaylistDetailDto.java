package com.butterzhub.muzic.dto;

import java.util.List;
import java.util.UUID;

public record PlaylistDetailDto(UUID id, String name, String youtubePlaylistId, List<SongDto> songs) {}
