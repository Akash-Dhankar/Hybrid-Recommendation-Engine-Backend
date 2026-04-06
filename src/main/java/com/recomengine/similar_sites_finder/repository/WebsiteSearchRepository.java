package com.recomengine.similar_sites_finder.repository;

import com.recomengine.similar_sites_finder.model.WebsiteDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WebsiteSearchRepository
        extends ElasticsearchRepository<WebsiteDocument, String> {

    List<WebsiteDocument> findByCategory(String category);

    List<WebsiteDocument> findByUrlContaining(String url);
}
