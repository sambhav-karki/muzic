package com.butterzhub.muzic.dto;

// When a controller parameter uses @RequestBody, Spring's JSON message converter
// maps the incoming "prompt" property to this record's constructor argument.
public record PromptRequest(String prompt) {
}
