package com.recomengine.similar_sites_finder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recomengine.similar_sites_finder.dto.SiteResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.ttl-hours:24}")
    private long cacheTtlHours;

    private static final String KEY_PREFIX = "similar:";

    // ── GET ──────────────────────────────────────────────────────
    // Returns null = cache miss (not the same as empty list)
    public List<SiteResultDto> get(String url) {
        String key = buildKey(url);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                log.debug("Cache MISS for: {}", key);
                return null;
            }
            log.debug("Cache HIT for: {}", key);
            return objectMapper.convertValue(
                    cached,
                    new TypeReference<List<SiteResultDto>>() {}
            );
        } catch (Exception e) {
            // Cache failures must NEVER break the main fallback flow
            log.warn("Redis GET failed for: {} — {}", key, e.getMessage());
            return null;
        }
    }

    // ── SET ──────────────────────────────────────────────────────
    public void set(String url, List<SiteResultDto> results) {
        if (results == null || results.isEmpty()) {
            log.debug("Skipping cache SET — empty results for: {}", url);
            return;
        }
        String key = buildKey(url);
        try {
            redisTemplate.opsForValue().set(
                    key,
                    results,
                    cacheTtlHours,
                    TimeUnit.HOURS
            );
            log.debug("Cache SET: {} ({} results, TTL={}h)",
                    key, results.size(), cacheTtlHours);
        } catch (Exception e) {
            // Non-fatal — log and let the response go through anyway
            log.warn("Redis SET failed for: {} — {}", key, e.getMessage());
        }
    }

    // ── EVICT ────────────────────────────────────────────────────
    public void evict(String url) {
        String key = buildKey(url);
        try {
            redisTemplate.delete(key);
            log.debug("Cache EVICT: {}", key);
        } catch (Exception e) {
            log.warn("Redis EVICT failed for: {} — {}", key, e.getMessage());
        }
    }

    // ── HEALTH CHECK ─────────────────────────────────────────────
    public boolean isHealthy() {
        try {
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return true;
        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    // Normalize URL so "GitHub.com" and "github.com" hit the same key
    private String buildKey(String url) {
        return KEY_PREFIX + url.toLowerCase().trim();
    }
}
