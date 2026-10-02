package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ShortLink;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, String> {
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ShortLink link set link.clickCount = link.clickCount + 1 "
			+ "where link.code = :code and (link.expiresAt is null or link.expiresAt > :now)")
	int incrementClickCountIfActive(@Param("code") String code, @Param("now") Instant now);

	@Query("select link.originalUrl from ShortLink link where link.code = :code")
	Optional<String> findOriginalUrlByCode(@Param("code") String code);
}