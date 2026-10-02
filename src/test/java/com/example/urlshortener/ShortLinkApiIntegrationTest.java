package com.example.urlshortener;

import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.repository.ShortLinkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ShortLinkApiIntegrationTest.FixedClockConfiguration.class)
class ShortLinkApiIntegrationTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShortLinkRepository repository;

    @BeforeEach
    void clearLinks() {
        repository.deleteAll();
    }

    @Test
    void createsShortLinkWithFutureExpiration() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com/articles/42","expiresAt":"2030-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/r/")))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/articles/42"));
    }

    @Test
    void rejectsNonHttpUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("originalUrl must be an absolute HTTP or HTTPS URL"));
    }

    @Test
    void redirectsAndIncrementsAnalytics() throws Exception {
        String response = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/target\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        String code = created.path("code").asText();

        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
        mockMvc.perform(get("/api/v1/links/" + code + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1));
    }

    @Test
    void returnsNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(get("/r/missing1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Short link not found"));
    }

    @Test
    void returnsGoneForExpiredLink() throws Exception {
        repository.saveAndFlush(new ShortLink("expired1", "https://example.com/old", FIXED_NOW.minusSeconds(60)));

        mockMvc.perform(get("/r/expired1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Short link has expired"));
    }

    @Test
    void rejectsExpirationInThePast() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/old\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("expiresAt must be in the future"));
    }

    @Test
    void treatsExpirationAtTheCurrentInstantAsExpired() throws Exception {
        repository.saveAndFlush(new ShortLink("boundary", "https://example.com/boundary", FIXED_NOW));

        mockMvc.perform(get("/r/boundary"))
                .andExpect(status().isGone());
    }

    @Test
    void concurrentRedirectsDoNotLoseClickCounts() throws Exception {
        String response = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/concurrent-clicks\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(response).path("code").asText();
        int requestCount = 24;
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> redirects = IntStream.range(0, requestCount)
                    .<Callable<Void>>mapToObj(index -> () -> {
                        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());
                        return null;
                    }).toList();
            for (var result : executor.invokeAll(redirects)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/v1/links/" + code + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(requestCount));
    }

    @Test
    void concurrentCreatesAllocateDistinctCodes() throws Exception {
        int requestCount = 24;
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> creates = IntStream.range(0, requestCount)
                    .<Callable<String>>mapToObj(index -> () -> {
                        String body = "{\"originalUrl\":\"https://example.com/item/" + index + "\"}";
                        String response = mockMvc.perform(post("/api/v1/links")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();
                        return objectMapper.readTree(response).path("code").asText();
                    }).toList();
            List<String> codes = executor.invokeAll(creates).stream().map(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent create failed", exception);
                }
            }).toList();
            org.junit.jupiter.api.Assertions.assertEquals(requestCount, codes.stream().distinct().count());
        } finally {
            executor.shutdownNow();
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}