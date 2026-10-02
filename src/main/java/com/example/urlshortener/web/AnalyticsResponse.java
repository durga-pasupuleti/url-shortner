package com.example.urlshortener.web;

import com.example.urlshortener.domain.ShortLink;

import java.time.Instant;

public record AnalyticsResponse(
        String code,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        long clickCount) {

    public static AnalyticsResponse from(ShortLink link) {
        return new AnalyticsResponse(link.getCode(), link.getOriginalUrl(), link.getCreatedAt(),
                link.getExpiresAt(), link.getClickCount());
    }
}