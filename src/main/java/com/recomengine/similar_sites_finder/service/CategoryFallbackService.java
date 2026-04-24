package com.recomengine.similar_sites_finder.service;

import com.recomengine.similar_sites_finder.dto.SiteResultDto;
import com.recomengine.similar_sites_finder.model.SimilarSite.ResultSource;
import com.recomengine.similar_sites_finder.model.Website;
import com.recomengine.similar_sites_finder.repository.jpa.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryFallbackService {

    private final WebsiteRepository websiteRepository;

    private static final int MAX_RESULTS = 10;

    // ── MAIN METHOD ───────────────────────────────────────────────
    // Finds other websites in the same category from PostgreSQL
    // Ordered by monthly visits — most popular first
    public List<SiteResultDto> findByCategory(String category,
                                              String excludeUrl) {
        if (category == null || category.isBlank()) {
            log.warn("Category fallback called with null/blank category");
            return getDefaultTrending(excludeUrl);
        }

        log.info("Category fallback for category: '{}', excluding: {}",
                category, excludeUrl);

        try {
            List<Website> websites = websiteRepository
                    .findByCategoryIgnoreCaseOrderByMonthlyVisitsDesc(category);

            if (websites.isEmpty()) {
                log.info("No sites found for category: {} — using trending",
                        category);
                return getDefaultTrending(excludeUrl);
            }

            List<SiteResultDto> results = websites.stream()
                    // Don't return the URL that was searched for
                    .filter(w -> !w.getUrl().equalsIgnoreCase(excludeUrl))
                    .limit(MAX_RESULTS)
                    .map(this::mapToDto)
                    .collect(Collectors.toList());

            log.info("Category fallback returned {} results for: {}",
                    results.size(), category);
            return results;

        } catch (Exception e) {
            log.warn("Category fallback failed: {}", e.getMessage());
            return getDefaultTrending(excludeUrl);
        }
    }

    // ── DEFAULT TRENDING ──────────────────────────────────────────
    // Final safety net — returns most visited sites from DB
    // This is Step 5 in our fallback chain
    public List<SiteResultDto> getDefaultTrending(String excludeUrl) {
        log.info("Returning default trending sites");
        try {
            List<Website> trending = websiteRepository
                    .findTopByMonthlyVisits(PageRequest.of(0, MAX_RESULTS + 1));

            return trending.stream()
                    .filter(w -> excludeUrl == null
                            || !w.getUrl().equalsIgnoreCase(excludeUrl))
                    .limit(MAX_RESULTS)
                    .map(this::mapToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Default trending failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── MAP Website → SiteResultDto ───────────────────────────────
    private SiteResultDto mapToDto(Website website) {
        return SiteResultDto.builder()
                .url(website.getUrl())
                .title(website.getTitle())
                .category(website.getCategory())
                .score(normalizeScore(website.getMonthlyVisits()))
                .source(ResultSource.CATEGORY_FALLBACK)
                .build();
    }

    private double normalizeScore(Long monthlyVisits) {
        if (monthlyVisits == null || monthlyVisits <= 0) return 0.1;
        double logScore = Math.log10(monthlyVisits) / 10.0;
        return Math.min(Math.max(logScore, 0.1), 0.6);
    }
}
