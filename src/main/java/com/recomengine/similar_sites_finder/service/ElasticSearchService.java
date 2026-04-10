package com.recomengine.similar_sites_finder.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.recomengine.similar_sites_finder.dto.SiteResultDto;
import com.recomengine.similar_sites_finder.model.SimilarSite.ResultSource;
import com.recomengine.similar_sites_finder.model.WebsiteDocument;
import com.recomengine.similar_sites_finder.repository.WebsiteSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final WebsiteSearchRepository websiteSearchRepository;

    @Value("${app.elasticsearch.max-results:10}")
    private int maxResults;

    private static final String INDEX = "websites";

    // ── MAIN SEARCH ───────────────────────────────────────────────
    // Multi-match query across keywords, category, description, title
    // Field boosting: keywords^3 means keyword matches score 3x higher
    public List<SiteResultDto> findSimilarByKeywords(String url,
                                                     String keywords,
                                                     String category) {
        log.info("ElasticSearch query for url: {}, keywords: {}, category: {}",
                url, keywords, category);

        String queryText = buildQueryText(keywords, category, url);
        if (queryText.isBlank()) {
            log.warn("No query text built — skipping ES search");
            return Collections.emptyList();
        }

        try {
            SearchResponse<WebsiteDocument> response = elasticsearchClient
                    .search(s -> s
                                    .index(INDEX)
                                    .size(maxResults)
                                    .query(q -> q
                                            .multiMatch(mm -> mm
                                                    .fields(
                                                            "keywords^3",
                                                            "category^2",
                                                            "title^1.5",
                                                            "description"
                                                    )
                                                    .query(queryText)
                                            )
                                    ),
                            WebsiteDocument.class
                    );

            return mapHitsToDto(response, url);

        } catch (Exception e) {
            log.warn("ElasticSearch query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── CATEGORY SEARCH ───────────────────────────────────────────
    public List<SiteResultDto> findByCategory(String category) {
        log.info("ElasticSearch category search: {}", category);
        try {
            SearchResponse<WebsiteDocument> response = elasticsearchClient
                    .search(s -> s
                                    .index(INDEX)
                                    .size(maxResults)
                                    .query(q -> q
                                            .term(t -> t
                                                    .field("category")
                                                    .value(category.toLowerCase())
                                            )
                                    ),
                            WebsiteDocument.class
                    );
            return mapHitsToDto(response, null);
        } catch (Exception e) {
            log.warn("ElasticSearch category search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── INDEX SINGLE WEBSITE ──────────────────────────────────────
    public void indexWebsite(WebsiteDocument document) {
        try {
            websiteSearchRepository.save(document);
            log.debug("Indexed: {}", document.getUrl());
        } catch (Exception e) {
            log.warn("Failed to index {}: {}", document.getUrl(), e.getMessage());
        }
    }

    // ── BULK INDEX ────────────────────────────────────────────────
    public void indexAll(List<WebsiteDocument> documents) {
        try {
            websiteSearchRepository.saveAll(documents);
            log.info("Bulk indexed {} websites", documents.size());
        } catch (Exception e) {
            log.warn("Bulk index failed: {}", e.getMessage());
        }
    }

    // ── MAP HITS → DTO ────────────────────────────────────────────
    private List<SiteResultDto> mapHitsToDto(
            SearchResponse<WebsiteDocument> response,
            String excludeUrl) {

        List<Hit<WebsiteDocument>> hits = response.hits().hits();
        if (hits.isEmpty()) {
            log.debug("ElasticSearch returned 0 hits");
            return Collections.emptyList();
        }

        List<SiteResultDto> results = new ArrayList<>();
        for (Hit<WebsiteDocument> hit : hits) {
            WebsiteDocument doc = hit.source();
            if (doc == null) continue;

            // Don't return the site that was searched for
            if (excludeUrl != null
                    && doc.getUrl().contains(excludeUrl)) continue;

            // Normalize ES score to 0.0 - 1.0 range
            double rawScore = hit.score() != null ? hit.score() : 0.5;
            double normalizedScore = Math.min(rawScore / 10.0, 1.0);

            results.add(SiteResultDto.builder()
                    .url(doc.getUrl())
                    .title(doc.getTitle())
                    .category(doc.getCategory())
                    .score(normalizedScore)
                    .source(ResultSource.ELASTICSEARCH)
                    .build());
        }

        log.info("ElasticSearch returned {} results", results.size());
        return results;
    }

    // ── BUILD QUERY TEXT ──────────────────────────────────────────
    // Combines keywords + category + domain name into one search string
    private String buildQueryText(String keywords,
                                  String category,
                                  String url) {
        StringBuilder sb = new StringBuilder();

        if (keywords != null && !keywords.isBlank()) {
            sb.append(keywords).append(" ");
        }
        if (category != null && !category.isBlank()) {
            sb.append(category).append(" ");
        }
        // Extract "github" from "https://github.com"
        if (url != null && !url.isBlank()) {
            String domain = url
                    .replaceAll("https?://(www\\.)?", "")
                    .split("\\.")[0];
            sb.append(domain);
        }

        return sb.toString().trim();
    }
}
