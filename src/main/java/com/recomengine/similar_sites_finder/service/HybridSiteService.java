package com.recomengine.similar_sites_finder.service;

import com.recomengine.similar_sites_finder.dto.SimilarSiteResponse;
import com.recomengine.similar_sites_finder.dto.SiteResultDto;
import com.recomengine.similar_sites_finder.model.SimilarSite;
import com.recomengine.similar_sites_finder.repository.jpa.SimilarSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSiteService {

    private final RedisCacheService redisCacheService;
    private final ExternalApiService externalApiService;
    private final ElasticSearchService elasticSearchService;
    private final CategoryFallbackService categoryFallbackService;
    private final KeywordExtractionService keywordExtractionService;
    private final SimilarSiteRepository similarSiteRepository;

    private static final int MAX_RESULTS = 10;

    // ── MAIN ORCHESTRATION METHOD ─────────────────────────────────
    // Implements the exact 5-step fallback chain
    public SimilarSiteResponse findSimilarSites(String url) {
        log.info("=== Finding similar sites for: {} ===", url);
        String normalizedUrl = normalizeUrl(url);

        // ── STEP 1: Redis Cache ───────────────────────────────────
        List<SiteResultDto> cached = redisCacheService.get(normalizedUrl);
        if (isValid(cached)) {
            log.info("STEP 1 HIT — Returning cached results for: {}", normalizedUrl);
            return buildResponse(normalizedUrl, "CACHE", cached);
        }
        log.info("STEP 1 MISS — No cache for: {}", normalizedUrl);

        // Extract keywords + category ONCE — reused across all steps
        String keywords = keywordExtractionService.extractKeywords(normalizedUrl);
        String category = keywordExtractionService.extractCategory(normalizedUrl);
        log.debug("Extracted — keywords: '{}', category: '{}'", keywords, category);

        // ── STEP 2: External API ──────────────────────────────────
        List<SiteResultDto> apiResults =
                externalApiService.fetchSimilarSites(normalizedUrl);

        if (isValid(apiResults)) {
            log.info("STEP 2 HIT — External API returned {} results", apiResults.size());
            saveAndCache(normalizedUrl, apiResults);
            return buildResponse(normalizedUrl, "EXTERNAL_API", apiResults);
        }
        log.info("STEP 2 MISS — External API returned nothing");

        // ── STEP 3: ElasticSearch ─────────────────────────────────
        List<SiteResultDto> esResults = elasticSearchService
                .findSimilarByKeywords(normalizedUrl, keywords, category);

        if (isValid(esResults)) {
            log.info("STEP 3 HIT — ElasticSearch returned {} results", esResults.size());
            saveAndCache(normalizedUrl, esResults);
            return buildResponse(normalizedUrl, "ELASTICSEARCH", esResults);
        }
        log.info("STEP 3 MISS — ElasticSearch returned nothing");

        // ── STEP 4: Category Fallback (PostgreSQL) ────────────────
        List<SiteResultDto> categoryResults =
                categoryFallbackService.findByCategory(category, normalizedUrl);

        if (isValid(categoryResults)) {
            log.info("STEP 4 HIT — Category fallback returned {} results",
                    categoryResults.size());
            redisCacheService.set(normalizedUrl, categoryResults);
            return buildResponse(normalizedUrl, "CATEGORY_FALLBACK", categoryResults);
        }
        log.info("STEP 4 MISS — No category results for: {}", category);

        // ── STEP 5: Default Trending ──────────────────────────────
        log.info("STEP 5 — Returning default trending sites");
        List<SiteResultDto> trending =
                categoryFallbackService.getDefaultTrending(normalizedUrl);
        redisCacheService.set(normalizedUrl, trending);
        return buildResponse(normalizedUrl, "DEFAULT_TRENDING", trending);
    }

    // ── ADVANCED: Hybrid Ranked Results ──────────────────────────
    // Combines API + ElasticSearch, deduplicates, ranks by score
    // Used when you want best-of-both rather than strict fallback
    public SimilarSiteResponse findSimilarSitesHybrid(String url) {
        log.info("=== Hybrid ranked search for: {} ===", url);
        String normalizedUrl = normalizeUrl(url);

        // Check cache first — same as regular flow
        List<SiteResultDto> cached = redisCacheService.get(normalizedUrl);
        if (isValid(cached)) {
            return buildResponse(normalizedUrl, "CACHE", cached);
        }

        String keywords = keywordExtractionService.extractKeywords(normalizedUrl);
        String category = keywordExtractionService.extractCategory(normalizedUrl);

        // Collect from BOTH sources simultaneously
        List<SiteResultDto> apiResults =
                externalApiService.fetchSimilarSites(normalizedUrl);
        List<SiteResultDto> esResults =
                elasticSearchService.findSimilarByKeywords(
                        normalizedUrl, keywords, category);

        // Merge + deduplicate + rank
        List<SiteResultDto> merged = mergeAndRank(apiResults, esResults);

        if (isValid(merged)) {
            saveAndCache(normalizedUrl, merged);
            return buildResponse(normalizedUrl, "HYBRID", merged);
        }

        // Fall through to category/trending if both empty
        List<SiteResultDto> fallback =
                categoryFallbackService.findByCategory(category, normalizedUrl);
        if (isValid(fallback)) {
            redisCacheService.set(normalizedUrl, fallback);
            return buildResponse(normalizedUrl, "CATEGORY_FALLBACK", fallback);
        }

        List<SiteResultDto> trending =
                categoryFallbackService.getDefaultTrending(normalizedUrl);
        redisCacheService.set(normalizedUrl, trending);
        return buildResponse(normalizedUrl, "DEFAULT_TRENDING", trending);
    }

    // ── HELPER: isValid ───────────────────────────────────────────
    // A result is valid if it's non-null and non-empty
    private boolean isValid(List<SiteResultDto> results) {
        return results != null && !results.isEmpty();
    }

    // ── HELPER: saveAndCache ──────────────────────────────────────
    // Persists results to PostgreSQL + caches in Redis
    private void saveAndCache(String sourceUrl, List<SiteResultDto> results) {
        // Cache in Redis
        redisCacheService.set(sourceUrl, results);

        // Persist to PostgreSQL — delete stale first, then insert fresh
        try {
            similarSiteRepository.deleteBySourceUrl(sourceUrl);

            List<SimilarSite> entities = results.stream()
                    .map(dto -> SimilarSite.builder()
                            .sourceUrl(sourceUrl)
                            .similarUrl(dto.getUrl())
                            .siteTitle(dto.getTitle())
                            .siteCategory(dto.getCategory())
                            .score(dto.getScore())
                            .resultSource(dto.getSource())
                            .build())
                    .collect(Collectors.toList());

            similarSiteRepository.saveAll(entities);
            log.debug("Saved {} results to PostgreSQL for: {}", entities.size(), sourceUrl);
        } catch (Exception e) {
            // DB write failure is non-fatal — cache still works
            log.warn("Failed to persist results for {}: {}", sourceUrl, e.getMessage());
        }
    }

    // ── HELPER: mergeAndRank ──────────────────────────────────────
    // Merges two result lists, boosts URLs appearing in both,
    // deduplicates, sorts by score descending, returns top N
    private List<SiteResultDto> mergeAndRank(
            List<SiteResultDto> apiResults,
            List<SiteResultDto> esResults) {

        // Map url → dto for deduplication
        Map<String, SiteResultDto> merged = new LinkedHashMap<>();

        // Add API results first
        for (SiteResultDto dto : apiResults) {
            if (dto.getUrl() != null) {
                merged.put(dto.getUrl().toLowerCase(), dto);
            }
        }

        // Merge ES results — if URL already exists from API, boost its score
        for (SiteResultDto dto : esResults) {
            if (dto.getUrl() == null) continue;
            String key = dto.getUrl().toLowerCase();

            if (merged.containsKey(key)) {
                // URL appears in BOTH sources — boost score by 20%
                SiteResultDto existing = merged.get(key);
                double boostedScore = Math.min(existing.getScore() * 1.2, 1.0);
                existing.setScore(boostedScore);
            } else {
                merged.put(key, dto);
            }
        }

        // Sort by score descending, return top MAX_RESULTS
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(SiteResultDto::getScore).reversed())
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    // ── HELPER: buildResponse ─────────────────────────────────────
    private SimilarSiteResponse buildResponse(String url,
                                              String source,
                                              List<SiteResultDto> results) {
        return SimilarSiteResponse.builder()
                .queriedUrl(url)
                .resolvedFrom(source)
                .totalResults(results.size())
                .results(results)
                .build();
    }

    // ── HELPER: normalizeUrl ──────────────────────────────────────
    private String normalizeUrl(String url) {
        if (url == null) return "";
        url = url.trim().toLowerCase();
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        return url.endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }
}
