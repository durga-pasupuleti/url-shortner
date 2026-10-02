package com.example.urlshortener.web;

import com.example.urlshortener.domain.ShortLink;

import java.time.Instant;

public record ShortLinkResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt) {

    public static ShortLinkResponse from(ShortLink link, String shortUrl) {
        return new ShortLinkResponse(link.getCode(), shortUrl, link.getOriginalUrl(),
                link.getCreatedAt(), link.getExpiresAt());
    }
}