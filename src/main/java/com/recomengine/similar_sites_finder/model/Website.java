package com.recomengine.similar_sites_finder.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "websites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Website {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 500)
    private String url;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "keywords", length = 1000)
    private String keywords;

    @Column(name = "domain_rank")
    private Integer domainRank;

    @Column(name = "monthly_visits")
    private Long monthlyVisits;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public List<String> getKeywordList() {
        if (keywords == null || keywords.isBlank()) return List.of();
        return List.of(keywords.split(","));
    }
}
