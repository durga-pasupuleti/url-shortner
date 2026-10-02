package com.example.urlshortener.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateShortLinkRequest(
        @NotBlank @Size(max = 2048) String originalUrl,
        Instant expiresAt) {
}