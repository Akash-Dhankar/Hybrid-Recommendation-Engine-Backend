package com.recomengine.similar_sites_finder.service;

import com.recomengine.similar_sites_finder.dto.ExternalApiResponse;
import com.recomengine.similar_sites_finder.dto.SiteResultDto;
import com.recomengine.similar_sites_finder.model.SimilarSite.ResultSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ExternalApiService {

    private final WebClient rapidApiWebClient;
    private final String rapidApiKey;
    private final String rapidApiHost;
    private final int maxResults;
    private final boolean useMock;

    public ExternalApiService(
            @Qualifier("rapidApiWebClient") WebClient rapidApiWebClient,
            @Value("${app.rapidapi.key}") String rapidApiKey,
            @Value("${app.rapidapi.host}") String rapidApiHost,
            @Value("${app.rapidapi.max-results:10}") int maxResults,
            @Value("${app.rapidapi.use-mock:false}") boolean useMock) {
        this.rapidApiWebClient = rapidApiWebClient;
        this.rapidApiKey = rapidApiKey;
        this.rapidApiHost = rapidApiHost;
        this.maxResults = maxResults;
        this.useMock = useMock;
    }

    // ── MAIN ENTRY POINT ──────────────────────────────────────────
    public List<SiteResultDto> fetchSimilarSites(String url) {
        if (useMock) {
            log.info("[MOCK] Returning mock data for: {}", url);
            return getMockResults(url);
        }
        return callRealApi(url);
    }

    // ── REAL API CALL ─────────────────────────────────────────────
    private List<SiteResultDto> callRealApi(String url) {
        String domain = extractDomain(url);
        if (domain == null) {
            log.warn("Could not extract domain from: {}", url);
            return Collections.emptyList();
        }

        log.info("Calling Similar Sites API for domain: {}", domain);

        try {
            ExternalApiResponse response = rapidApiWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/data")
                            .queryParam("domain", domain)
                            .build())
                    .header("x-rapidapi-key", rapidApiKey)
                    .header("x-rapidapi-host", rapidApiHost)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r ->
                            r.bodyToMono(String.class).flatMap(body -> {
                                log.error("API 4xx: {} body: {}", r.statusCode(), body);
                                return reactor.core.publisher.Mono.error(
                                        new RuntimeException("API 4xx: " + r.statusCode()));
                            })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, r ->
                            r.bodyToMono(String.class).flatMap(body -> {
                                log.error("API 5xx: {} body: {}", r.statusCode(), body);
                                return reactor.core.publisher.Mono.error(
                                        new RuntimeException("API 5xx: " + r.statusCode()));
                            })
                    )
                    .bodyToMono(ExternalApiResponse.class)
                    .block();

            return mapToDto(response);

        } catch (WebClientRequestException e) {
            log.warn("Network error for {}: {}", domain, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Unexpected error for {}: {}", domain, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── MAP RESPONSE → DTO ────────────────────────────────────────
    private List<SiteResultDto> mapToDto(ExternalApiResponse response) {
        if (response == null
                || response.getSimilarSites() == null
                || response.getSimilarSites().isEmpty()) {
            log.debug("External API returned empty response");
            return Collections.emptyList();
        }

        List<String> domains = response.getSimilarSites();
        List<SiteResultDto> results = new ArrayList<>();

        // double[] used instead of AtomicDouble — works in lambdas
        // without needing Guava dependency
        double score = 0.95;

        for (int i = 0; i < Math.min(domains.size(), maxResults); i++) {
            String domain = domains.get(i);
            if (domain == null || domain.isBlank()) continue;

            results.add(SiteResultDto.builder()
                    .url("https://" + domain)
                    .title(formatTitle(domain))
                    .category(null)
                    .score(score)
                    .source(ResultSource.EXTERNAL_API)
                    .build());

            score = score - 0.05; // decrement per position
        }

        log.info("External API returned {} similar sites", results.size());
        return results;
    }

    // ── MOCK DATA ─────────────────────────────────────────────────
    private List<SiteResultDto> getMockResults(String url) {
        String domain = extractDomain(url);
        String key = domain != null ? domain.toLowerCase() : "";

        List<String> mockDomains;

        if (key.contains("github")) {
            mockDomains = List.of("gitlab.com", "bitbucket.org",
                    "sourceforge.net", "codeberg.org", "gitea.io");
        } else if (key.contains("stackoverflow")) {
            mockDomains = List.of("stackexchange.com", "dev.to",
                    "hashnode.com", "quora.com", "reddit.com");
        } else if (key.contains("youtube")) {
            mockDomains = List.of("vimeo.com", "dailymotion.com",
                    "twitch.tv", "rumble.com", "odysee.com");
        } else if (key.contains("amazon")) {
            mockDomains = List.of("ebay.com", "walmart.com",
                    "etsy.com", "shopify.com", "aliexpress.com");
        } else {
            mockDomains = List.of("wikipedia.org", "reddit.com",
                    "medium.com", "quora.com", "linkedin.com");
        }

        List<SiteResultDto> results = new ArrayList<>();
        double score = 0.95;

        for (String d : mockDomains) {
            results.add(SiteResultDto.builder()
                    .url("https://" + d)
                    .title(formatTitle(d))
                    .category(null)
                    .score(score)
                    .source(ResultSource.EXTERNAL_API)
                    .build());
            score = score - 0.05;
        }

        return results;
    }

    // ── HELPERS ───────────────────────────────────────────────────

    // "stackoverflow.com" → "Stackoverflow"
    private String formatTitle(String domain) {
        if (domain == null || domain.isBlank()) return null;
        String name = domain.contains(".")
                ? domain.substring(0, domain.indexOf('.'))
                : domain;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // "https://www.github.com/features" → "github.com"
    public String extractDomain(String url) {
        try {
            String normalized = url.startsWith("http") ? url : "https://" + url;
            URI uri = new URI(normalized);
            String host = uri.getHost();
            if (host == null) return null;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            log.warn("Failed to parse URL: {}", url);
            return null;
        }
    }
}