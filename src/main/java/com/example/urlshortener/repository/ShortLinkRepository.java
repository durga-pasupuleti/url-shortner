package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortLinkRepository extends JpaRepository<ShortLink, String> {
}