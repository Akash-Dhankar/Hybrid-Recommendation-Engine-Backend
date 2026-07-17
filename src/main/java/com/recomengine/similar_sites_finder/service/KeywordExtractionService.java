package com.recomengine.similar_sites_finder.service;

import com.recomengine.similar_sites_finder.model.Website;
import com.recomengine.similar_sites_finder.model.WebsiteDocument;
import com.recomengine.similar_sites_finder.repository.jpa.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractionService {

    private final WebsiteRepository websiteRepository;
    private final ElasticSearchService elasticSearchService;

    public String extractKeywords(String url) {
        if (url == null || url.isBlank()) return "";

        Optional<Website> existing = websiteRepository.findByUrl(
                normalizeUrl(url));

        if (existing.isPresent()
                && existing.get().getKeywords() != null
                && !existing.get().getKeywords().isBlank()) {
            log.debug("Keywords found in DB for: {}", url);
            return existing.get().getKeywords();
        }

        String domain = extractDomain(url);
        String keywords = extractFromDomain(domain);

        log.debug("Extracted keywords for {}: {}", url, keywords);
        return keywords;
    }

    public String extractCategory(String url) {
        if (url == null || url.isBlank()) return "general";

        Optional<Website> existing = websiteRepository.findByUrl(
                normalizeUrl(url));
        if (existing.isPresent()
                && existing.get().getCategory() != null) {
            return existing.get().getCategory();
        }

        String domain = extractDomain(url);
        return getCategoryForDomain(domain);
    }

    public void storeWebsiteIfNew(String url,String title,String keywords, String category) {
        String normalizedUrl = normalizeUrl(url);

        if (websiteRepository.existsByUrl(normalizedUrl)) {
            log.debug("Website already exists: {}", normalizedUrl);
            return;
        }

        try {
            Website website = Website.builder()
                    .url(normalizedUrl)
                    .title(title)
                    .category(category != null ? category : "general")
                    .keywords(keywords)
                    .build();
            websiteRepository.save(website);
            log.debug("Saved new website to PostgreSQL: {}", normalizedUrl);

            WebsiteDocument doc = WebsiteDocument.builder()
                    .id(normalizedUrl.replaceAll("https?://", "")
                            .replaceAll("[^a-zA-Z0-9]", "-"))
                    .url(normalizedUrl)
                    .title(title)
                    .keywords(keywords)
                    .category(category != null ? category : "general")
                    .description(title + " - " + category)
                    .build();
            elasticSearchService.indexWebsite(doc);
            log.debug("Indexed new website in ES: {}", normalizedUrl);

        } catch (Exception e) {
            log.warn("Failed to store website {}: {}", url, e.getMessage());
        }
    }

    private String extractFromDomain(String domain) {
        if (domain == null) return "website internet";

        Map<String, String> keywordMap = getKeywordMap();
        String lowerDomain = domain.toLowerCase();

        if (keywordMap.containsKey(lowerDomain)) {
            return keywordMap.get(lowerDomain);
        }

        for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
            if (lowerDomain.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String domainName = lowerDomain.contains(".")
                ? lowerDomain.substring(0, lowerDomain.indexOf('.'))
                : lowerDomain;
        return domainName + " website internet";
    }

    private String getCategoryForDomain(String domain) {
        if (domain == null) return "general";
        String lower = domain.toLowerCase();

        if (lower.matches(".*(github|gitlab|bitbucket|stackoverflow|" +
                "leetcode|dev\\.to|hashnode|codepen|replit).*"))
            return "technology";

        if (lower.matches(".*(youtube|twitch|netflix|vimeo|" +
                "dailymotion|hulu|disney).*"))
            return "entertainment";

        if (lower.matches(".*(amazon|ebay|etsy|shopify|" +
                "walmart|flipkart|aliexpress).*"))
            return "ecommerce";

        if (lower.matches(".*(cnn|bbc|reuters|techcrunch|" +
                "theguardian|nytimes|forbes).*"))
            return "news";

        if (lower.matches(".*(reddit|quora|twitter|linkedin|" +
                "facebook|instagram|discord).*"))
            return "social";

        if (lower.matches(".*(coursera|udemy|edx|freecodecamp|" +
                "w3schools|khanacademy|pluralsight).*"))
            return "education";

        if (lower.matches(".*(medium|hashnode|blogger|" +
                "wordpress|substack|ghost).*"))
            return "blogging";

        return "general";
    }

    private Map<String, String> getKeywordMap() {
        Map<String, String> map = new LinkedHashMap<>();

        map.put("github.com",
                "code repository git open-source collaboration version-control");
        map.put("gitlab.com",
                "code repository git devops ci-cd pipelines open-source");
        map.put("bitbucket.org",
                "code repository git version-control atlassian teams");
        map.put("stackoverflow.com",
                "programming questions answers debugging developers community");
        map.put("dev.to",
                "programming articles tutorials developers blogging community");
        map.put("hashnode.com",
                "developers blogging programming articles community");
        map.put("leetcode.com",
                "coding challenges algorithms data-structures interview preparation");
        map.put("codepen.io",
                "frontend code playground html css javascript design");
        map.put("replit.com",
                "online ide coding browser programming collaboration");

        map.put("youtube.com",
                "video streaming entertainment music tutorials vlogs creators");
        map.put("twitch.tv",
                "live streaming gaming esports entertainment creators");
        map.put("vimeo.com",
                "video streaming creative professionals filmmakers");
        map.put("dailymotion.com",
                "video streaming entertainment news music");
        map.put("netflix.com",
                "streaming movies shows entertainment subscription");

        map.put("amazon.com",
                "ecommerce shopping retail products delivery marketplace");
        map.put("ebay.com",
                "ecommerce auction shopping marketplace products");
        map.put("etsy.com",
                "ecommerce handmade vintage craft marketplace");
        map.put("shopify.com",
                "ecommerce platform online-store business retail");
        map.put("walmart.com",
                "ecommerce retail shopping grocery products");

        map.put("cnn.com",
                "news media politics world breaking journalism");
        map.put("bbc.com",
                "news media journalism world politics uk broadcasting");
        map.put("reuters.com",
                "news wire journalism world business finance");
        map.put("techcrunch.com",
                "technology news startups venture-capital innovation");
        map.put("theguardian.com",
                "news media journalism world politics culture");

        map.put("reddit.com",
                "community discussion forums social news voting");
        map.put("quora.com",
                "questions answers community knowledge discussion");
        map.put("twitter.com",
                "social media microblogging news discussion trending");
        map.put("linkedin.com",
                "professional network jobs career business social");
        map.put("discord.com",
                "community chat gaming voice social messaging");

        map.put("coursera.org",
                "online courses learning university certificates degrees");
        map.put("udemy.com",
                "online courses learning programming business skills");
        map.put("freecodecamp.org",
                "programming learning tutorials javascript web-development free");
        map.put("w3schools.com",
                "web development tutorials html css javascript learning");
        map.put("khanacademy.org",
                "education learning math science free courses");

        map.put("medium.com",
                "articles blogging writing technology programming stories");
        map.put("substack.com",
                "newsletter blogging writing publishing subscription");
        map.put("wordpress.com",
                "blogging cms website publishing content");

        return map;
    }

    public String extractDomain(String url) {
        try {
            String normalized = url.startsWith("http")
                    ? url : "https://" + url;
            URI uri = new URI(normalized);
            String host = uri.getHost();
            if (host == null) return null;
            return host.startsWith("www.")
                    ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        url = url.trim().toLowerCase();
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}