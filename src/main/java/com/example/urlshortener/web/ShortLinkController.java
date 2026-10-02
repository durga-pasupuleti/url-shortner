package com.example.urlshortener.web;

import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.service.ShortLinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/links")
public class ShortLinkController {
    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortLinkResponse> create(@Valid @RequestBody CreateShortLinkRequest request) {
        ShortLink link = service.create(request.originalUrl(), request.expiresAt());
        String baseUrl = service.shortUrl(link.getCode());
        URI shortUri = URI.create(baseUrl);
        return ResponseEntity.created(shortUri).body(ShortLinkResponse.from(link, baseUrl));
    }

    @GetMapping("/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code) {
        return AnalyticsResponse.from(service.getAnalytics(code));
    }

}