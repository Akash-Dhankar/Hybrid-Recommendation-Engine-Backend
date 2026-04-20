package com.recomengine.similar_sites_finder.controller;

import com.recomengine.similar_sites_finder.dto.SimilarSiteResponse;
import com.recomengine.similar_sites_finder.service.HybridSiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allows Chrome Extension to call this API
public class SimilarSiteController {

    private final HybridSiteService hybridSiteService;

    // ── PRIMARY ENDPOINT ──────────────────────────────────────────
    // Standard fallback chain: cache → API → ES → category → trending
    // Example: GET /api/similar?url=github.com
    @GetMapping("/similar")
    public ResponseEntity<SimilarSiteResponse> getSimilarSites(
            @RequestParam String url) {

        log.info("Request received — url: {}", url);

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SimilarSiteResponse response = hybridSiteService.findSimilarSites(url);
        return ResponseEntity.ok(response);
    }

    // ── HYBRID ENDPOINT ───────────────────────────────────────────
    // Combines API + ElasticSearch, ranks by score
    // Example: GET /api/similar/hybrid?url=github.com
    @GetMapping("/similar/hybrid")
    public ResponseEntity<SimilarSiteResponse> getSimilarSitesHybrid(
            @RequestParam String url) {

        log.info("Hybrid request received — url: {}", url);

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SimilarSiteResponse response = hybridSiteService.findSimilarSitesHybrid(url);
        return ResponseEntity.ok(response);
    }

    // ── HEALTH CHECK ──────────────────────────────────────────────
    // Used to verify the backend is running before Chrome Extension calls it
    // Example: GET /api/health
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Similar Sites Finder is running");
    }
}
