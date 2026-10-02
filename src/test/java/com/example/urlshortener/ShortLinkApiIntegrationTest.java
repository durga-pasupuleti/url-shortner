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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShortLinkApiIntegrationTest {
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
        repository.saveAndFlush(new ShortLink("expired1", "https://example.com/old", Instant.now().minusSeconds(60)));

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
}