package com.example.urlshortener.service;

import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.repository.ShortLinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

@Service
public class ShortLinkService {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortLinkRepository repository;
    private final Clock clock;
    private final String publicBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public ShortLinkService(ShortLinkRepository repository, Clock clock,
            @Value("${shortener.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.repository = repository;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public ShortLink create(String originalUrl, Instant expiresAt) {
        validateUrl(originalUrl);
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
        }

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (repository.existsById(code)) {
                continue;
            }
            try {
                return repository.saveAndFlush(new ShortLink(code, originalUrl, expiresAt));
            } catch (DataIntegrityViolationException collision) {
                if (attempt == MAX_CODE_ATTEMPTS - 1) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Could not allocate a unique short code", collision);
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate a short code");
    }

    @Transactional
    public String resolveAndRecordClick(String code) {
        int updated = repository.incrementClickCountIfActive(code, clock.instant());
        if (updated == 0) {
            ShortLink link = repository.findById(code)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
            if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(clock.instant())) {
                throw new ResponseStatusException(HttpStatus.GONE, "Short link has expired");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not record the redirect");
        }
        return repository.findOriginalUrlByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
    }

    @Transactional(readOnly = true)
    public ShortLink getAnalytics(String code) {
        return repository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
    }

    public String shortUrl(String code) {
        return publicBaseUrl + "/r/" + code;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "originalUrl must be an absolute HTTP or HTTPS URL");
        }
    }
}