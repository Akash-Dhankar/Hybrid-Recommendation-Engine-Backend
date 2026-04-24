package com.recomengine.similar_sites_finder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.recomengine.similar_sites_finder.repository.jpa"
)
@EnableElasticsearchRepositories(
        basePackages = "com.recomengine.similar_sites_finder.repository.elastic"
)
public class RepositoryConfig {
}