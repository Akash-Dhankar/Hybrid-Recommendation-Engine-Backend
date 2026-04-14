package com.recomengine.similar_sites_finder.service;


import com.recomengine.similar_sites_finder.model.WebsiteDocument;
import com.recomengine.similar_sites_finder.repository.WebsiteSearchRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSeederService {

    private final WebsiteSearchRepository websiteSearchRepository;

    @PostConstruct
    public void seedElasticSearch() {
        if (websiteSearchRepository.count() > 0) {
            log.info("ElasticSearch already seeded — skipping");
            return;
        }

        log.info("Seeding ElasticSearch...");

        List<WebsiteDocument> websites = List.of(
                // Technology
                build("https://github.com", "GitHub",
                        "code repository version control git open-source collaboration",
                        "technology", 1_000_000_000L),
                build("https://gitlab.com", "GitLab",
                        "code repository git devops ci-cd open-source pipelines",
                        "technology", 300_000_000L),
                build("https://bitbucket.org", "Bitbucket",
                        "code repository git version control atlassian teams",
                        "technology", 100_000_000L),
                build("https://stackoverflow.com", "Stack Overflow",
                        "programming questions answers developers debugging community",
                        "technology", 500_000_000L),
                build("https://dev.to", "DEV Community",
                        "programming articles tutorials developers blogging",
                        "technology", 50_000_000L),
                build("https://hashnode.com", "Hashnode",
                        "developers blogging programming articles community",
                        "technology", 20_000_000L),
                build("https://leetcode.com", "LeetCode",
                        "coding challenges algorithms data structures interview",
                        "technology", 80_000_000L),
                build("https://freecodecamp.org", "freeCodeCamp",
                        "programming learning tutorials javascript web development",
                        "education", 100_000_000L),
                build("https://w3schools.com", "W3Schools",
                        "web development tutorials html css javascript learning",
                        "education", 300_000_000L),
                // Entertainment
                build("https://youtube.com", "YouTube",
                        "video streaming entertainment music tutorials vlogs",
                        "entertainment", 30_000_000_000L),
                build("https://vimeo.com", "Vimeo",
                        "video streaming creative professionals filmmakers",
                        "entertainment", 300_000_000L),
                build("https://twitch.tv", "Twitch",
                        "live streaming gaming esports video entertainment",
                        "entertainment", 1_000_000_000L),
                build("https://dailymotion.com", "Dailymotion",
                        "video streaming entertainment news music",
                        "entertainment", 200_000_000L),
                // Ecommerce
                build("https://amazon.com", "Amazon",
                        "ecommerce shopping online retail products delivery",
                        "ecommerce", 3_000_000_000L),
                build("https://ebay.com", "eBay",
                        "ecommerce shopping auction online marketplace",
                        "ecommerce", 1_000_000_000L),
                build("https://etsy.com", "Etsy",
                        "ecommerce handmade vintage craft marketplace",
                        "ecommerce", 400_000_000L),
                build("https://shopify.com", "Shopify",
                        "ecommerce platform online store business retail",
                        "ecommerce", 200_000_000L),
                // News
                build("https://cnn.com", "CNN",
                        "news media politics world breaking journalism",
                        "news", 800_000_000L),
                build("https://bbc.com", "BBC",
                        "news media journalism world politics uk",
                        "news", 700_000_000L),
                build("https://reuters.com", "Reuters",
                        "news wire journalism world business finance",
                        "news", 300_000_000L),
                build("https://techcrunch.com", "TechCrunch",
                        "technology news startups venture capital innovation",
                        "news", 100_000_000L),
                // Community
                build("https://reddit.com", "Reddit",
                        "community discussion forums social news voting",
                        "community", 2_000_000_000L),
                build("https://quora.com", "Quora",
                        "questions answers community knowledge discussion",
                        "community", 500_000_000L),
                build("https://twitter.com", "Twitter",
                        "social media microblogging news discussion trending",
                        "social", 1_500_000_000L),
                build("https://linkedin.com", "LinkedIn",
                        "professional network jobs career business social",
                        "social", 1_000_000_000L),
                build("https://medium.com", "Medium",
                        "articles blogging writing technology programming",
                        "blogging", 200_000_000L)
        );

        websiteSearchRepository.saveAll(websites);
        log.info("Seeded {} websites into ElasticSearch", websites.size());
    }

    private WebsiteDocument build(String url, String title,
                                  String keywords, String category,
                                  Long monthlyVisits) {
        return WebsiteDocument.builder()
                .id(url.replaceAll("https?://", "")
                        .replaceAll("[^a-zA-Z0-9]", "-"))
                .url(url)
                .title(title)
                .description(title + " - " + category)
                .keywords(keywords)
                .category(category)
                .monthlyVisits(monthlyVisits)
                .build();
    }
}