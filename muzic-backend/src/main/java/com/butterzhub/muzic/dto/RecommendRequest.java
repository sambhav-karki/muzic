package com.butterzhub.muzic.dto;

import jakarta.validation.constraints.NotBlank;

// Jackson binds the JSON prompt property through the record's canonical constructor.
public record RecommendRequest(@NotBlank String prompt) {
}
