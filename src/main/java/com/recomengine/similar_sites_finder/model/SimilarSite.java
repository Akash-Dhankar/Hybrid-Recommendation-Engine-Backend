package com.recomengine.similar_sites_finder.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "similar_sites",
        indexes = {
                @Index(name = "idx_source_url", columnList = "source_url"),
                @Index(name = "idx_similar_url", columnList = "similar_url")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarSite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "similar_url", nullable = false, length = 500)
    private String similarUrl;

    @Column(name = "site_title", length = 200)
    private String siteTitle;

    @Column(name = "site_category", length = 100)
    private String siteCategory;

    // 0.0 to 1.0 — higher is more similar
    @Column(nullable = false)
    private Double score;

    // Where did this result come from?
    @Enumerated(EnumType.STRING)
    @Column(name = "result_source", length = 50)
    private ResultSource resultSource;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ResultSource {
        EXTERNAL_API,
        ELASTICSEARCH,
        CATEGORY_FALLBACK,
        DEFAULT_TRENDING
    }
}
